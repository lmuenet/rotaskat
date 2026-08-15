# Rotaskat

Punkte-Tracker fuer private Skatrunden. Android-App mit eigenem Server fuer
die All-Time-Rangliste eines "Skatvereins".

> **Status: fruehe Phase.** Domaenenmodell und Abrechnungslogik stehen und
> sind getestet. Die App ist bisher ein Platzhalter, der Server kennt nur
> `/health`. Der Funktionsumfang wird gerade festgelegt.

## Was die App koennen soll

Gespielt wird zu dritt oder zu viert, bei vier setzt einer aus. Nach jeder
Runde wird erfasst, wer Alleinspieler war, was gespielt wurde und wie es
ausging. Die App rechnet die Punkte aus, fuehrt den Sessionstand und
synchronisiert am Ende mit dem Server, der die vereinsweite Rangliste haelt.

Der Nutzungskontext bestimmt das Design: Eingabe passiert am Spieltisch,
zwischen zwei Runden, oft einhaendig und mit schlechtem Empfang. Deshalb ist
die App **offline-first** und die Eingabegeschwindigkeit das wichtigste
Qualitaetsmerkmal.

## Techstack

| Teil | Technologie |
|------|-------------|
| App | Kotlin, Jetpack Compose, Material 3, Room, Navigation Compose, DataStore |
| Sync | Ktor-Client, client-generierte UUIDs pro Runde fuer Idempotenz |
| Server | Kotlin, Ktor, Exposed, Flyway |
| Datenbank | PostgreSQL 17 |
| Betrieb | Docker Compose hinter Caddy mit automatischem TLS |
| Auth | Vereins-Einladungscode, danach geraetegebundener Token |
| Verteilung | Signierte APK ueber GitHub Releases |

## Aufbau

```
rotaskat/
├── shared/    Domaenenmodell und Abrechnung. Reine Kotlin-JVM-Library.
├── server/    Ktor-Server, Postgres-Schema, Flyway-Migrationen.
├── app/       Android-App.
├── deploy/    Caddy-Konfiguration.
└── docs/      SCORING.md - die verbindlichen Hausregeln.
```

Der entscheidende Punkt ist `shared`: Modell und Abrechnung existieren genau
**einmal** und werden von App und Server gemeinsam benutzt. Der Server kann
damit jede vom Client gelieferte Runde nachrechnen, statt ihr zu vertrauen.

Ein zweiter Grundsatz: gespeichert werden die **rohen Spielfakten**, nicht die
fertigen Punkte. Aendert sich eine Hausregel, wird die gesamte Historie neu
berechnet. Details in [docs/SCORING.md](docs/SCORING.md).

## Abrechnung in Kurzform

Mit Spielwert `V` (dem offiziellen Reizwert nach Skatordnung):

- Alleinspieler **gewinnt**: `+V`, jeder Gegenspieler `-V/2`
- Alleinspieler **verliert**: `-2V`, jeder Gegenspieler `+V`
- **Ramsch**: der Spieler mit den meisten Augen `A` bekommt `-A`, die anderen
  beiden je `+A/2`
- Der **Aussetzende** am Vierertisch bekommt 0

Jede Runde summiert sich damit zu genau 0. Diese Invariante ist per
Property-Test und per Datenbank-Constraint abgesichert.

Punkte werden intern als ganzzahlige **halbe Punkte** gefuehrt (Wert x 2),
damit die Haelftung bei ungeraden Spielwerten exakt bleibt.

## Entwicklung

Voraussetzung ist ein JDK 17 und das Android SDK, beides bringt Android Studio
mit. Der Gradle-Wrapper ist eingecheckt.

```bash
./gradlew :shared:test        # Abrechnungslogik testen
./gradlew :app:assembleDebug  # APK bauen
./gradlew :server:run         # Server lokal starten (braucht Postgres)
```

### Windows: Umlaute im Pfad

Wenn der Windows-Benutzername einen Umlaut enthaelt, schlaegt `:shared:test`
mit `ClassNotFoundException: GradleWorkerMain` fehl. Gradle legt den Classpath
des Test-Workers als Jar unterhalb von `GRADLE_USER_HOME` ab, und der
Standardpfad `C:\Users\<Name>\.gradle` bricht dann das Classloading.

Abhilfe: das Gradle-Home auf einen reinen ASCII-Pfad legen.

```powershell
setx GRADLE_USER_HOME "C:\dev\gradle-home"
```

Aus demselben Grund sollte auch das Projekt selbst nicht unterhalb eines
Pfades mit Umlaut liegen.

## Betrieb

Konfiguration kommt vollstaendig aus der Umgebung, im Repo liegt nur
`.env.example`. Auf dem Server:

```bash
cp .env.example .env    # ausfuellen: DB-Passwort, Domain, ACME-Mail
docker compose up -d --build
```

Caddy holt sich das TLS-Zertifikat selbst. Die Datenbank ist nicht nach aussen
gemappt und nur im Compose-Netz erreichbar.

## Konfiguration

| Variable | Wofuer |
|----------|--------|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Datenbank |
| `ROTASKAT_DOMAIN` | Domain des Servers, Caddy zieht darauf das Zertifikat |
| `ACME_EMAIL` | Let's-Encrypt-Benachrichtigungen |
| `ROTASKAT_SERVER_URL` | Basis-URL, die in die APK gebacken wird |

Keine dieser Angaben steht im Repo. Fuer CI-Builds liegen sie in den
GitHub-Actions-Secrets.

## Lizenz

[Apache License 2.0](LICENSE). Bewusst gewaehlt, weil JSkat und
now-in-android ebenfalls unter Apache-2.0 stehen und wir deren Testvektoren
bzw. Muster mit Attribution uebernehmen koennen. Uebernommener Fremdcode wird
in [NOTICE](NOTICE) gefuehrt.
