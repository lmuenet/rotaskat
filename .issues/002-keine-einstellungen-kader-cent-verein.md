---
titel: Nach dem Einstieg gibt es keinen Weg mehr zu Kader, Cent-Satz oder Vereinsbeitritt
typ: fehlende Funktion
schwere: hoch
bereich: Navigation, Einstellungen, Onboarding
status: offen
gefunden: 2026-09-27, Funktionstest auf Pixel 10 Pro
---

## Beobachtung

Im lokalen Modus bietet die Übersicht nur „Rangliste“ und „Statistik“. Es gibt
keinen Einstellungsbildschirm und kein Menü. Damit sind nach dem ersten Start
drei Dinge nicht mehr erreichbar:

1. **Spieler anlegen oder umbenennen.** Wer beim Einrichten nur vier Spieler
   eingetragen hat, kann keinen fünften mehr hinzufügen. Beim neuen Abend
   stehen nur die ursprünglichen Namen zur Auswahl.
2. **Cent je Punkt ändern.** Der Satz wird nur in `LocalSetupScreen` abgefragt.
3. **Von lokal zu einem Verein wechseln.** `RotaskatNavActions.toJoin()` wird
   nur aus dem `OnboardingScreen` aufgerufen. Nach dem Einstieg startet die App
   immer in `Routes.HOME`, der Beitritt ist nicht mehr erreichbar.

Punkt 3 widerspricht direkt `docs/SCOPE.md` und dem README: „Der Wechsel von
lokal zu Verein ist jederzeit möglich und nimmt die bereits gespielten Abende
mit.“ Die Datenschicht dafür gibt es schon (`RotaskatRepository.adoptLocalData`),
es fehlt nur der Weg dorthin.

## Reproduktion

1. App im Modus „Ohne Verein“ einrichten.
2. In der Übersicht nach einer Möglichkeit suchen, einen Spieler hinzuzufügen
   oder einem Verein beizutreten → es gibt keine.

## Vorschlag

- Einen Bildschirm „Verein“ bzw. „Einstellungen“ in der Kopfzeile der Übersicht
  (Zahnrad oder Überlauf-Menü) mit:
  - Kader: Spieler hinzufügen, umbenennen und (ohne gespielte Runden) entfernen.
    Im Vereinsmodus nur lesend, der Kader kommt vom Server.
  - Cent je Punkt, gilt ab dem nächsten Abend (laufende Abende frieren den
    Satz bereits ein).
  - „Einem Verein beitreten“: führt in den bestehenden `JoinScreen` samt
    Zuordnung der lokalen Spieler.
  - Anzeige von Modus, Server und Sync-Status (siehe #006).
- Falls der Kader im Vereinsmodus bewusst serverseitig bleibt, das in der
  Oberfläche so sagen statt die Funktion einfach wegzulassen.
