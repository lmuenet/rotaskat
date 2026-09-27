---
titel: Vier-Tap-Pfad bricht auf dem Gerät - Spitzen und Zusätze liegen unter der Falz, Scrollposition bleibt nach dem Speichern stehen
typ: bug / UX
schwere: hoch
bereich: Rundeneingabe (SessionScreen, RoundEntryPanel)
status: offen
gefunden: 2026-09-27, Pixel 10 Pro (1080×2410, Standard-Schriftgröße)
---

## Beobachtung

SCOPE.md legt für den Standardfall vier Taps fest:
Alleinspieler → Farbe/Grand → Spitzen → Gewonnen/Verloren.

Auf einem großen Telefon mit Standard-Schriftgröße passt davon nur ein Teil auf
den Bildschirm:

- Stand, Geberzeile, Alleinspieler und Spielart füllen den Scrollbereich. Die
  **Spitzen sind nicht sichtbar**, man muss erst scrollen. Ohne Scrollen bleibt
  der Vorgabewert „mit 1“ stehen, und genau hier ist ein falscher Spielwert
  am wahrscheinlichsten, weil man den Fehler nicht sieht.
- Der Scrollbereich ist durch die feste Ergebnisleiste (Spielwert-Karte plus
  zwei 200-px-Buttons) sehr niedrig. Bei langen Beschreibungen wie „Kreuz mit 2,
  Hand, Schneider, … = 108“ wächst die Karte auf drei Zeilen und schiebt den
  Scrollbereich weiter zusammen.
- **Nach dem Speichern bleibt die Scrollposition stehen.** Wer für Spitzen oder
  Zusätze gescrollt hat, sieht für die nächste Runde die Alleinspieler-Auswahl
  nicht mehr und muss erst zurückscrollen.
- „Zusätze aufklappen“ öffnet den Bereich unterhalb des sichtbaren Teils, ohne
  hinzuscrollen. Es sieht so aus, als wäre nichts passiert.

## Reproduktion

1. Laufenden Abend öffnen, Alleinspieler und Kreuz wählen.
2. Die Spitzen sind nicht zu sehen.
3. Runterscrollen, Spitzen 2, „Gewonnen“ → die nächste Runde beginnt mitten im
   Formular.

## Vorschlag

- Nach jedem Speichern `scrollState.animateScrollTo(0)`.
- Die Spitzen kompakter machen, z. B. eine einzeilige Reihe 1–4 plus „mehr“, weil
  Werte über 4 selten sind. Direkt unter die Spielart setzen, sodass
  Alleinspieler, Spielart und Spitzen ohne Scrollen sichtbar sind.
- Den Stand im Abend kompakter darstellen (eine Zeile statt Name/Zahl/„gibt“
  untereinander) oder beim Scrollen einklappen.
- Die Spielwert-Karte auf eine Zeile begrenzen und die ausführliche
  Aufschlüsselung einklappen.
- Beim Aufklappen der Zusätze `bringIntoViewRequester` verwenden.
- Als Regressionstest einen Screenshot- oder Compose-UI-Test auf 360×640 dp,
  der prüft, dass die Spitzen ohne Scrollen sichtbar sind.
