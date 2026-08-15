-- Alles, was der Sync braucht. V1__init.sql wird NICHT angefasst, sie ist
-- bereits angewendet - eine angewendete Migration nachtraeglich zu aendern
-- laesst Flyway beim naechsten Start mit einer Checksummen-Abweichung stehen.

-- Der Delta-Pull haengt an einem SERVERSEITIGEN Zaehler, nicht an einem
-- Zeitstempel. Handyuhren laufen auseinander; ein Client mit vorgehender Uhr
-- wuerde beim naechsten Pull genau die Runden ueberspringen, die er noch nie
-- gesehen hat. Eine Sequenz ist global monoton und kennt dieses Problem nicht.
-- Bewusst EINE Sequenz fuer alle Tabellen: der Cursor eines Clients ist damit
-- eine einzige Zahl statt eines Zaehlers je Entitaet.
CREATE SEQUENCE sync_seq AS BIGINT START WITH 1;

-- --- Verein ----------------------------------------------------------------

-- Der Cent-pro-Punkt-Satz haengt am Verein. Die Session friert ihn zusaetzlich
-- ein: wer den Satz aendert, aendert damit bewusst NICHT die Abrechnung
-- laufender oder vergangener Abende.
ALTER TABLE club
    ADD COLUMN cents_per_point INTEGER NOT NULL DEFAULT 10
        CHECK (cents_per_point >= 0);

-- --- Geraet ----------------------------------------------------------------

-- Ein zurueckgezogenes Geraet wird nicht geloescht, sondern stillgelegt: der
-- Audit-Pfad "welches Geraet hat diesen Abend geschrieben" bleibt so lesbar.
-- Kein eigener Index auf token_hash: V1 hat darauf schon ein UNIQUE, und der
-- Lookup beim Auth-Provider geht genau darueber.
ALTER TABLE device
    ADD COLUMN revoked_at TIMESTAMPTZ;

-- --- Session ---------------------------------------------------------------

-- Die Abrechnungsregeln liegen als Kopie an der Session, nicht als Verweis auf
-- den Verein. Sonst saehe der Spieler am Tisch andere Zahlen als spaeter in der
-- Rangliste, sobald jemand eine Hausregel aendert. scoring_version haelt fest,
-- mit welchem Stand des Algorithmus gerechnet wurde - eine Abweichung zwischen
-- APK und Server ist damit erkennbar statt bloss verwirrend.
ALTER TABLE session
    ADD COLUMN scoring_config  JSONB   NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN scoring_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN cents_per_point INTEGER NOT NULL DEFAULT 10 CHECK (cents_per_point >= 0),
    -- Geber der naechsten Runde. Am Vierertisch folgt daraus der Aussetzende,
    -- deshalb wird nur der Geber fortgeschrieben.
    ADD COLUMN dealer_seat     SMALLINT NOT NULL DEFAULT 0 CHECK (dealer_seat >= 0 AND dealer_seat < 4),
    ADD COLUMN revision        BIGINT  NOT NULL DEFAULT 1 CHECK (revision >= 1),
    -- Hash der kanonischen Nutzlast. Nur damit laesst sich "gleiche Revision,
    -- gleicher Inhalt" (idempotente Wiederholung) von "gleiche Revision,
    -- anderer Inhalt" (Konflikt) unterscheiden.
    ADD COLUMN content_hash    TEXT    NOT NULL DEFAULT '',
    ADD COLUMN updated_seq     BIGINT  NOT NULL DEFAULT nextval('sync_seq'),
    -- Loeschen ist immer ein Tombstone, nie ein DELETE: nur so kann der Sync
    -- die Loeschung ueberhaupt zu den anderen Geraeten tragen.
    ADD COLUMN deleted_at      TIMESTAMPTZ;

CREATE INDEX session_club_seq_idx ON session (club_id, updated_seq);

-- --- Runde -----------------------------------------------------------------

ALTER TABLE round
    -- Am Vierertisch ist der Geber derselbe Sitz wie sitting_out_seat. Beide
    -- Spalten bleiben trotzdem: sitting_out_seat ist das, was die Abrechnung
    -- braucht, dealer_seat das, was die Rotation fortschreibt.
    ADD COLUMN dealer_seat  SMALLINT NOT NULL DEFAULT 0 CHECK (dealer_seat >= 0 AND dealer_seat < 4),
    ADD COLUMN revision     BIGINT   NOT NULL DEFAULT 1 CHECK (revision >= 1),
    ADD COLUMN content_hash TEXT     NOT NULL DEFAULT '',
    ADD COLUMN updated_seq  BIGINT   NOT NULL DEFAULT nextval('sync_seq'),
    -- Die vom Client gerechneten halben Punkte, nur als Pruefsumme. Der Server
    -- rechnet mit :shared neu und speichert seinen eigenen Wert in
    -- round_score. Hier liegt die Client-Sicht, damit eine Versionsdrift
    -- zwischen APK und Server im Nachhinein noch belegbar ist.
    ADD COLUMN client_half_points JSONB,
    -- Gesetzt, sobald Client- und Serverwert auseinanderlaufen. Als Spalte statt
    -- nur als Logzeile, weil ein Logeintrag nach zwei Wochen weg ist und die
    -- Frage "seit wann rechnet die alte APK falsch" dann unbeantwortbar waere.
    ADD COLUMN score_mismatch_at TIMESTAMPTZ;

-- Bestandsdaten nachziehen, BEVOR die Pruefung dazukommt: vor V2 war der Geber
-- nicht gespeichert, am Vierertisch ist er aber eindeutig aus dem Aussetzenden
-- ableitbar. Andersherum wuerde die Pruefung an genau diesen Altdaten
-- scheitern und die Migration mittendrin stehenbleiben.
UPDATE round SET dealer_seat = sitting_out_seat WHERE sitting_out_seat IS NOT NULL;

-- Der Aussetzende gibt: laeuft das auseinander, rotiert die App an einer
-- anderen Stellung weiter, als abgerechnet wurde. Die Pruefung steht schon in
-- Scoring.validate(), hier noch einmal als Netz gegen direkte Schreibzugriffe.
ALTER TABLE round
    ADD CONSTRAINT round_dealer_sits_out
        CHECK (sitting_out_seat IS NULL OR sitting_out_seat = dealer_seat);

-- Traegt den Delta-Pull: die Runden einer Session ab einem Cursor.
CREATE INDEX round_seq_idx ON round (updated_seq);
CREATE INDEX round_session_seq_idx ON round (session_id, updated_seq);

-- --- Rangliste je Saison ---------------------------------------------------

-- Eine Saison ist ein Kalenderjahr. Der Schnitt laeuft ueber session.started_at
-- in UTC, nicht ueber die Rundenzeit: ein Abend, der nach Mitternacht
-- weiterlaeuft, gehoert vollstaendig zu dem Jahr, in dem er angefangen hat.
CREATE VIEW leaderboard_season AS
SELECT EXTRACT(YEAR FROM s.started_at AT TIME ZONE 'UTC')::INTEGER AS season,
       p.club_id,
       p.id                                    AS player_id,
       p.display_name,
       COUNT(DISTINCT r.id)                    AS rounds_played,
       COUNT(DISTINCT s.id)                    AS sessions_played,
       SUM(rs.half_points)                     AS total_half_points,
       COUNT(DISTINCT r.id) FILTER (
           WHERE r.declarer_seat = rs.seat
           )                                   AS games_declared,
       COUNT(DISTINCT r.id) FILTER (
           WHERE r.declarer_seat = rs.seat AND r.won
           )                                   AS games_declared_won
FROM player p
         JOIN session_seat ss ON ss.player_id = p.id
         JOIN session s ON s.id = ss.session_id AND s.deleted_at IS NULL
         JOIN round r ON r.session_id = s.id AND r.deleted_at IS NULL
         JOIN round_score rs ON rs.round_id = r.id AND rs.seat = ss.seat
GROUP BY season, p.club_id, p.id, p.display_name;
