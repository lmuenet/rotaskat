---
titel: Ramsch - Speichern trotz falscher Augensumme, falsche Bestätigung, Tastatur verdeckt die Eingabe
typ: bug
schwere: mittel
bereich: Rundeneingabe, RamschPanel, SessionViewModel
status: offen
gefunden: 2026-09-27
---

## Beobachtung

1. **Summe ≠ 120 lässt sich speichern.** Mit 60/40/30 zeigt die App „Summe 130
   statt 120 - bitte nachzaehlen“, der Button „Ramsch eintragen“ bleibt aber aktiv
   (nur rot eingefärbt) und speichert die Runde. Laut Kommentar im `RamschPanel`
   ist die Summenprobe als Sicherheitsnetz gedacht. Ohne Sperre fängt sie nichts ab.
2. **Falsche Bestätigung:** Nach dem Speichern eines Ramsch sagt die Leiste
   „Verloren gespeichert“ (`SessionViewModel.commit` kennt nur
   gewonnen/verloren).
3. **Tastatur:** Die Zifferntastatur verdeckt das zweite und dritte Feld. Die
   Aktionstaste ist ein Haken ohne Wirkung statt „Weiter“ (kein
   `ImeAction.Next`/`KeyboardActions`). Man muss die Tastatur schließen,
   scrollen und das nächste Feld antippen.
4. Die Wertanzeige zeigt schon nach dem ersten Feld „60 Augen = 60“, obwohl
   noch gar nicht feststeht, wer verliert.

## Vorschlag

- „Ramsch eintragen“ erst aktivieren, wenn die Summe 120 ergibt, oder den
  Button bei abweichender Summe in „Trotzdem eintragen (Summe 130)“ umbenennen.
- Das dritte Feld automatisch als `120 − a − b` vorbelegen. Das spart eine
  Eingabe, und die Summenprobe entfällt als Fehlerquelle.
- `ImeAction.Next` für die ersten Felder, `ImeAction.Done` für das letzte,
  `Modifier.imePadding()` bzw. `bringIntoView` für das fokussierte Feld.
- Snackbar-Text für Ramsch: „Ramsch gespeichert – <Verlierer> −60“.
- Den Wert erst anzeigen, wenn alle Augen eingetragen sind.
