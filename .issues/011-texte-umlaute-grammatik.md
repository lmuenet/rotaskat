---
titel: Oberflächentexte - ASCII-Umschreibungen statt Umlaute, Grammatik- und Logikfehler
typ: bug / Text
schwere: niedrig (aber überall sichtbar)
bereich: alle Bildschirme
status: erledigt (Ramsch-Meldung siehe #005)
gefunden: 2026-09-27
---

## Umlaute

Die Oberfläche übernimmt die ae/oe/ue/ss-Schreibweise aus Code und Doku. Auf
dem Bildschirm wirkt das wie ein Kodierungsfehler. Beispiele:

„laeuft“, „aendern“, „Rueckgaengig“, „Zurueck“, „Zusaetze“, „Ueberreizt“,
„waehlen“, „geloescht“, „Runde loeschen“, „koennen“, „Haeufigstes
Alleinspiel“, „Schuebe“, „So wenige Zahlungen wie moeglich“,
„Gezaehlt werden alle nicht geloeschten Runden“, „staerker gezeichnet“.

Vorschlag: Alle Nutzertexte nach `res/values/strings.xml` (UTF-8) verschieben.
Das bringt richtige Umlaute, erlaubt später Übersetzungen und trennt Text von
Logik. Mindestens aber in den Kotlin-Literalen echte Umlaute verwenden.

## Grammatik

- Statistik: „1 Abende, 4 Runden mitgespielt“
  (`StatsScreen.kt:84`, ohne Plural-Unterscheidung; in `LeaderboardScreen`
  ist es richtig gelöst). Besser `pluralStringResource`.
- Rangliste: „allein 100 % (2 von 2)“ bricht mitten in der Klammer um.

## Logik

- „Haeufigstes Alleinspiel: Kreuz – 1 von 2 Alleinspielen“ bei Gleichstand
  (Kreuz 1×, Null 1×). Bei Gleichstand beide nennen oder „keins vorn“.
- Die Rückgängig-Leiste sagt beim Ramsch „Verloren gespeichert“ (siehe #005).
