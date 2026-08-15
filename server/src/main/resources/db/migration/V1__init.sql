-- Rotaskat Grundschema.
--
-- Leitgedanke: gespeichert werden die ROHEN Spielfakten, nicht die fertigen
-- Punkte. round.declaration haelt die Ansage als JSON, round_score haelt die
-- daraus berechneten halben Punkte. Aendert sich eine Hausregel, wird
-- round_score neu berechnet und die Historie bleibt korrekt.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Ein Skatverein. Die Hausregeln haengen am Verein, nicht am Code.
CREATE TABLE club (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            TEXT        NOT NULL,
    invite_code     TEXT        NOT NULL UNIQUE,
    scoring_config  JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE player (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id      UUID        NOT NULL REFERENCES club (id) ON DELETE CASCADE,
    display_name TEXT        NOT NULL,
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (club_id, display_name)
);

-- Geraetegebundener Token statt Passwort. Gespeichert wird nur der Hash.
CREATE TABLE device (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id    UUID        NOT NULL REFERENCES player (id) ON DELETE CASCADE,
    token_hash   TEXT        NOT NULL UNIQUE,
    label        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ
);

-- Ein Spielabend.
CREATE TABLE session (
    id         UUID PRIMARY KEY,
    club_id    UUID        NOT NULL REFERENCES club (id) ON DELETE CASCADE,
    seat_count SMALLINT    NOT NULL CHECK (seat_count IN (3, 4)),
    status     TEXT        NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'closed')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at   TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Sitzordnung: welcher Spieler sitzt auf welchem Platz. Plaetze sind 0-basiert.
CREATE TABLE session_seat (
    session_id UUID     NOT NULL REFERENCES session (id) ON DELETE CASCADE,
    seat       SMALLINT NOT NULL CHECK (seat >= 0 AND seat < 4),
    player_id  UUID     NOT NULL REFERENCES player (id) ON DELETE RESTRICT,
    PRIMARY KEY (session_id, seat),
    UNIQUE (session_id, player_id)
);

-- Eine gespielte Runde. Die id wird vom Client erzeugt, damit ein erneut
-- gesendeter Sync nicht doppelt zaehlt.
CREATE TABLE round (
    id               UUID PRIMARY KEY,
    session_id       UUID        NOT NULL REFERENCES session (id) ON DELETE CASCADE,
    sequence         INTEGER     NOT NULL,
    declarer_seat    SMALLINT,
    sitting_out_seat SMALLINT,
    declaration      JSONB       NOT NULL,
    won              BOOLEAN     NOT NULL DEFAULT FALSE,
    contra           TEXT        NOT NULL DEFAULT 'NONE' CHECK (contra IN ('NONE', 'KONTRA', 'RE')),
    bid              INTEGER,
    overbid          BOOLEAN     NOT NULL DEFAULT FALSE,
    ramsch           JSONB,
    game_value       INTEGER     NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Korrekturen loeschen nicht hart, damit der Sync sie propagieren kann.
    deleted_at       TIMESTAMPTZ,
    UNIQUE (session_id, sequence)
);

CREATE INDEX round_session_idx ON round (session_id) WHERE deleted_at IS NULL;

-- Abgeleitete Punkte, serverseitig aus den Rohdaten neu berechnet. In HALBEN
-- Punkten: gespeicherter Wert = tatsaechliche Punkte * 2.
CREATE TABLE round_score (
    round_id    UUID     NOT NULL REFERENCES round (id) ON DELETE CASCADE,
    seat        SMALLINT NOT NULL CHECK (seat >= 0 AND seat < 4),
    half_points INTEGER  NOT NULL,
    PRIMARY KEY (round_id, seat)
);

-- Die zentrale Invariante: jede Runde ist nullsummig. Als DEFERRABLE
-- Constraint umgesetzt, damit die Zeilen einer Runde in einer Transaktion
-- geschrieben werden koennen und erst beim Commit geprueft wird.
CREATE FUNCTION assert_round_is_zero_sum() RETURNS TRIGGER AS $$
DECLARE
    total INTEGER;
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

CREATE CONSTRAINT TRIGGER round_score_zero_sum
    AFTER INSERT OR UPDATE OR DELETE ON round_score
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_round_is_zero_sum();

-- All-Time-Rangliste als View. Bei vier Spielern liefert der Aussetzende
-- keine Zeile in round_score mit Wirkung, er wird mit 0 gefuehrt.
CREATE VIEW leaderboard AS
SELECT
    p.club_id,
    p.id                                   AS player_id,
    p.display_name,
    COUNT(DISTINCT r.id)                   AS rounds_played,
    SUM(rs.half_points)                    AS total_half_points,
    SUM(rs.half_points) / 2.0              AS total_points,
    COUNT(DISTINCT r.id) FILTER (
        WHERE r.declarer_seat = rs.seat AND r.won
    )                                      AS games_declared_won,
    COUNT(DISTINCT r.id) FILTER (
        WHERE r.declarer_seat = rs.seat
    )                                      AS games_declared
FROM player p
         JOIN session_seat ss ON ss.player_id = p.id
         JOIN round r ON r.session_id = ss.session_id AND r.deleted_at IS NULL
         JOIN round_score rs ON rs.round_id = r.id AND rs.seat = ss.seat
GROUP BY p.club_id, p.id, p.display_name;
