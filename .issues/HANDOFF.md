# Übergabe: Rotaskat – Issues abarbeiten, Umlaut-Fix, danach Design

## Ziel der nächsten Session

1. Die funktionalen Issues aus `.issues/` beheben, in der Reihenfolge der
   Priorität (siehe `.issues/README.md`, Spalte „Schwere“).
2. Alle Oberflächentexte auf echte Umlaute umstellen (Details unten).
3. Erst danach: Design-Überarbeitung auf Basis von `.issues/DESIGN-NOTIZEN.md`,
   mit Design-Plugins/Skills, nicht im selben Schritt wie die Funktionsfixes.

Repo: `C:\Users\lmueller\Documents\GitHub\rotaskat\rotaskat`, Branch `main`
(@ 6abea4c). Android-App in `app/` (Kotlin, Compose, M3, Room); Regeln und
Entscheidungen stehen in `docs/SCOPE.md` und `docs/SCORING.md`. Diese beiden
sind verbindlich, bitte vor Änderungen an der Eingabe lesen.

## Quellen – nicht duplizieren, dort nachlesen

- `.issues/README.md`: Index aller Issues, was geprüft wurde und was nicht
- `.issues/001` bis `013-*.md`: je Beobachtung, Reproduktion, Ursache mit
  Datei/Zeile und Vorschlag
- `.issues/DESIGN-NOTIZEN.md`: Look & Feel, Barrierefreiheit, offene Fragen
- `docs/SCOPE.md`: Vier-Tap-Budget, Undo statt Rückfragen, lokal→Verein

## Aktueller Stand im Arbeitsverzeichnis

- **Uncommittet:** `app/src/main/AndroidManifest.xml` hat
  `<uses-permission android:name="android.permission.VIBRATE" />` bekommen
  (Sofort-Fix für #001, nur für den Test eingetragen). #001 ist damit zur
  Hälfte erledigt; es fehlt noch die Absicherung in
  `ui/common/Haptics.kt` (`try/catch SecurityException`) und ein Test.
- **Untracked:** `.issues/` (noch nicht committet, der Nutzer hat nicht
  entschieden, ob der Ordner ins Repo soll → nachfragen oder mit committen,
  wenn der erste Fix committet wird).
- `local.properties` wurde angelegt (`sdk.dir=C:/Users/lmueller/AppData/Local/Android/Sdk`,
  gitignored). Ohne sie scheitert `installDebug` mit „SDK location not found“.
- Commits nur auf Nachfrage; Commit-Messages im Projekt sind deutsch, ASCII,
  ein Satz im Imperativ/Präsens (siehe `git log`).

## Umlaut-Fix (Issue #011) – Umfang und Vorgehen

Befund: Die UI-Texte übernehmen die ae/oe/ue-Schreibweise von Code und Doku
(„laeuft“, „Rueckgaengig“, „Zusaetze“, „Ueberreizt“, „waehlen“, „geloescht“,
„koennen“, „Haeufigstes“, „Schuebe“ …). Grob 18 Dateien unter
`app/src/main/kotlin/io/rotaskat/app/ui/` mit ~80 Fundstellen.
`app/src/main/res/values/strings.xml` enthält bisher nur `app_name`.

Empfehlung (vom Nutzer gewünscht ist der Fix, das Wie ist offen):
- Nutzertexte nach `res/values/strings.xml` verschieben (UTF-8, echte Umlaute),
  Aufruf über `stringResource`/`pluralStringResource`. Plurale mit
  `<plurals>` lösen – das behebt nebenbei „1 Abende“ (`ui/eval/StatsScreen.kt:84`).
- Wenn der Umbau auf Resources zu groß ist: mindestens die Kotlin-Literale
  auf echte Umlaute umstellen. Datei-Encoding ist UTF-8, Kotlin kann das.
- **Nicht anfassen:** Kommentare, Bezeichner, Doku, Commit-Messages – dort ist
  ASCII die Projektkonvention. Nur was auf dem Bildschirm landet.
- Auch prüfen: Fehlermeldungen aus `:shared` (z. B. `Scoring.validate()`),
  die in der Snackbar landen (`SessionMessage.Failed`), sowie
  `ui/common/Labels.kt`, `ui/common/EvalFormat.kt`, `ui/round/RoundDraft.kt`
  (baut die Beschreibung „Kreuz mit 2 … ueberreizt auf …“).
- Gründlich suchen: `grep -rnE '"[^"]*(ae|oe|ue|Ae|Oe|Ue)[^"]*"'` liefert
  auch echte Wörter ohne Umlaut („Neuen Abend“, „aktuell“, „Quote“,
  „Steuer“) – jede Stelle von Hand prüfen, nicht blind ersetzen. „ss“→„ß“
  ebenfalls nur gezielt (z. B. „Schluss“ bleibt, „grosse“ → „große“).
- Tests unter `app/src/test` referenzieren derzeit keine UI-Texte; nach dem
  Umbau `./gradlew :app:testDebugUnitTest` laufen lassen.

## Vorgeschlagene Reihenfolge

1. #001 fertig machen (Haptics absichern, Test) → committen.
2. #011 Umlaut-Fix (berührt viele Dateien – vor den UI-Fixes erledigen, sonst
   Merge-Schmerz mit sich selbst).
3. #003 (Vier-Tap-Pfad/Scroll), #004 (Überreizt), #005 (Ramsch), #008, #007,
   #009, #006.
4. #002 (Einstellungen/Kader/Vereinsbeitritt) ist der größte Brocken und
   braucht vorher eine kurze Designentscheidung mit dem Nutzer (wo liegt der
   Einstieg, was darf lokal vs. Verein) → `superpowers:brainstorming`.
5. #010, #012, #013 nach Absprache.
6. Design-Runde separat (DESIGN-NOTIZEN.md).

## Verifizieren auf dem Gerät

- Kein Emulator möglich (kein SVM im BIOS). Getestet wird auf dem **Pixel 10 Pro
  des Nutzers** per Wireless-ADB. Gekoppelt ist es bereits; nach Neustart des
  Handys ändert sich der Port: `adb mdns services` zeigt ihn,
  dann `adb connect 192.168.178.44:<port>`.
- mobile-mcp ist in Claude Code eingetragen (Tools `mcp__mobile-mcp__*`,
  per ToolSearch laden). Paket: `io.rotaskat.app.debug`.
- Build/Install: `./gradlew :app:installDebug`.
- Vor dem Testen App-Daten sichern, danach zurückspielen (der Nutzer hat
  echte Abende auf dem Gerät). Das Vorgehen, das funktioniert hat:
  - Sichern: `adb exec-out run-as io.rotaskat.app.debug tar cf - databases files > backup.tar`
  - Zurück: tar nach `/data/local/tmp` pushen, `chmod 644`, dann
    `run-as … sh -c 'tar xf /data/local/tmp/….tar'`, App vorher `am force-stop`.
  - In Git Bash **`MSYS_NO_PATHCONV=1`** setzen, sonst werden `/data/...`-Pfade
    zu `C:/Program Files/Git/data/...`.
  - Letzte Sicherung: `C:\Users\lmueller\rotaskat-backup\appdata-2026-09-27.tar`.
- Stolperfallen mit mobile-mcp (aus dieser Session gelernt):
  - Refs (`@e70`) werden gegen einen **neuen** UI-Dump aufgelöst. Ist die
    Snackbar schon weg, trifft der Ref ein anderes Element (so wurde versehentlich
    „Abend beenden“ geöffnet). Für flüchtige Elemente Koordinaten in einem
    `mobile_batch_commands` direkt nach der auslösenden Aktion verwenden.
  - Ein Tap direkt nach einem Swipe stoppt nur das Nachscrollen – zweimal tippen
    oder kurz warten.
  - Screenshots direkt nach Navigation zeigen oft noch den Crossfade; erneut
    aufnehmen, bevor ein Befund notiert wird.
  - Nach jedem Batch `mobile_get_foreground_app` prüfen – bei einem Absturz
    landen weitere Taps sonst in der darunterliegenden App.
  - `adb kill-server`/`taskkill adb.exe` trennt die WLAN-Verbindung.
- Logs: `adb logcat -v time '*:W'` im Hintergrund in eine Datei.

## Empfohlene Skills für die nächste Session

- `superpowers:systematic-debugging` bzw. `superpowers:test-driven-development`
  für die Bugfixes (#001, #004, #005, #008).
- `superpowers:brainstorming` vor #002 (neuer Bildschirm, Architekturfrage).
- `superpowers:verification-before-completion` vor jeder Erfolgsmeldung –
  Fixes auf dem Gerät nachprüfen, nicht nur `assembleDebug`.
- Für die spätere Design-Runde: `design:design-critique`,
  `design:accessibility-review`, `design:ux-copy` (Texte kürzen, siehe
  DESIGN-NOTIZEN „erklärende Absätze“), ggf. `frontend-design:frontend-design`.

## Hinweise zum Nutzer

- Antwortet und schreibt auf Deutsch; in Nutzertexten korrekte Umlaute.
- SSH/VPS-Hinweise aus der globalen CLAUDE.md sind für diese Arbeit nicht
  relevant (Server wird nicht angefasst).
