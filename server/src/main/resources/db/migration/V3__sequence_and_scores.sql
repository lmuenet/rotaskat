-- Nacharbeiten aus einem Audit. V1 und V2 sind angewendet und werden nicht
-- angefasst - eine nachtraeglich geaenderte Migration laesst Flyway beim
-- naechsten Start mit einer Checksummen-Abweichung stehen.

-- --- sequence ist Anzeigereihenfolge, keine Zusicherung -------------------

-- V1 legte UNIQUE (session_id, sequence) an. Die Position vergibt aber der
-- Client aus rein lokalem Wissen (MAX(sequence) + 1). Hat ein uebernommenes
-- Geraet die Historie nur teilweise gezogen - der Delta-Pull liefert seitenweise
-- und bricht in der Kneipe gern mittendrin ab -, vergibt es eine Position, die
-- auf dem Server bereits belegt ist. Der Push lief damit in einen
-- unique_violation, der Batch ist atomar, die Antwort war ein 500er, und weil
-- dieselbe Runde bei jedem Lauf wieder ansteht, blieb der Sync dieses Geraets
-- dauerhaft stehen.
--
-- Die Zusicherung war den Preis nicht wert: sequence ordnet die Rundenliste,
-- mehr nicht. Sortiert wird ueber (sequence, id), und weil die Id eine
-- zeitsortierte UUIDv7 ist, ist auch ein Gleichstand eindeutig geordnet - genau
-- so, wie die App es lokal ohnehin haelt.
ALTER TABLE round DROP CONSTRAINT IF EXISTS round_session_id_sequence_key;

-- Der Index bleibt, ohne Eindeutigkeit: er traegt die Rundenliste eines Abends.
CREATE INDEX IF NOT EXISTS round_session_sequence_idx ON round (session_id, sequence, id);

-- --- halbe Punkte als BIGINT ---------------------------------------------

-- Die beiden Ranglisten-Views haengen an half_points und muessen deshalb weg,
-- bevor die Spalte breiter wird: Postgres aendert den Typ einer Spalte nicht,
-- solange eine View darauf zeigt. Sie werden weiter unten neu angelegt - und
-- dabei gleich richtiggestellt.
DROP VIEW IF EXISTS leaderboard_season;
DROP VIEW IF EXISTS leaderboard;

-- SCOPE.md legt fest: halbe Punkte durchgehend ganzzahlig, in Postgres BIGINT.
-- V1 hatte INTEGER. Fuer eine regulaere Runde reicht das (das Maximum liegt bei
-- 4 * 264 * 4), die breitere Spalte ist trotzdem richtig: ein in Int gewrappter
-- Wert liesse sich klaglos in eine Int-Spalte schreiben, waehrend BIGINT den
-- Wert der Long-Referenzrechnung aufnimmt und den Ueberlauf sichtbar macht.
ALTER TABLE round_score ALTER COLUMN half_points TYPE BIGINT;

-- Dieselbe Ueberlegung fuer die Summe des Nullsummen-Triggers: in einer
-- INTEGER-Variablen braeche er bei kaputten Grossbetraegen mit
-- 'integer out of range' ab statt mit der vorgesehenen Meldung.
CREATE OR REPLACE FUNCTION assert_round_is_zero_sum() RETURNS TRIGGER AS $$
DECLARE
    total BIGINT;
    target_round UUID;
BEGIN
    target_round := COALESCE(NEW.round_id, OLD.round_id);
    SELECT COALESCE(SUM(half_points), 0) INTO total
    FROM round_score WHERE round_id = target_round;

    IF total <> 0 THEN
        RAISE EXCEPTION 'Runde % ist nicht nullsummig: Summe = %', target_round, total;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- --- Ranglisten ------------------------------------------------------------

-- Zwei Abweichungen zur App, die beide dieselbe Zahl betrafen:
--
--  1. Ein ueberreiztes Spiel gilt IMMER als verloren, auch wenn der
--     Alleinspieler die Stiche hatte (Scoring.kt: lost = overbid || !won). Die
--     View zaehlte es als Sieg, die App nicht.
--  2. Am Vierertisch schreibt der Server auch fuer den Aussetzenden eine Zeile
--     in round_score, mit 0. Die View zaehlte sie als gespielte Runde mit; jede
--     Quote, die darauf aufsetzt, lag damit um ein Viertel daneben.
CREATE VIEW leaderboard_season AS
SELECT EXTRACT(YEAR FROM s.started_at AT TIME ZONE 'UTC')::INTEGER AS season,
       p.club_id,
       p.id                                    AS player_id,
       p.display_name,
       COUNT(DISTINCT r.id) FILTER (
           WHERE r.sitting_out_seat IS DISTINCT FROM rs.seat
           )                                   AS rounds_played,
       COUNT(DISTINCT s.id) FILTER (
           WHERE r.sitting_out_seat IS DISTINCT FROM rs.seat
           )                                   AS sessions_played,
       SUM(rs.half_points)                     AS total_half_points,
       COUNT(DISTINCT r.id) FILTER (
           WHERE r.declarer_seat = rs.seat
           )                                   AS games_declared,
       COUNT(DISTINCT r.id) FILTER (
           WHERE r.declarer_seat = rs.seat AND r.won AND NOT r.overbid
           )                                   AS games_declared_won
FROM player p
         JOIN session_seat ss ON ss.player_id = p.id
         JOIN session s ON s.id = ss.session_id AND s.deleted_at IS NULL
         JOIN round r ON r.session_id = s.id AND r.deleted_at IS NULL
         JOIN round_score rs ON rs.round_id = r.id AND rs.seat = ss.seat
GROUP BY season, p.club_id, p.id, p.display_name;

CREATE VIEW leaderboard AS
SELECT
    p.club_id,
    p.id                                   AS player_id,
    p.display_name,
    COUNT(DISTINCT r.id) FILTER (
        WHERE r.sitting_out_seat IS DISTINCT FROM rs.seat
    )                                      AS rounds_played,
    SUM(rs.half_points)                    AS total_half_points,
    SUM(rs.half_points) / 2.0              AS total_points,
    COUNT(DISTINCT r.id) FILTER (
        WHERE r.declarer_seat = rs.seat AND r.won AND NOT r.overbid
    )                                      AS games_declared_won,
    COUNT(DISTINCT r.id) FILTER (
        WHERE r.declarer_seat = rs.seat
    )                                      AS games_declared
FROM player p
         JOIN session_seat ss ON ss.player_id = p.id
         JOIN round r ON r.session_id = ss.session_id AND r.deleted_at IS NULL
         JOIN round_score rs ON rs.round_id = r.id AND rs.seat = ss.seat
GROUP BY p.club_id, p.id, p.display_name;
