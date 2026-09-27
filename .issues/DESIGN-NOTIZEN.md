# Look & Feel – Notizen für die Design-Runde

Beobachtungen vom Funktionstest am 27.09.2026 (Pixel 10 Pro, Dark Theme,
Standard-Schriftgröße). Das ist kein Auftrag, sondern eine Sammlung von Punkten,
die für die Design-Überarbeitung wichtig sein dürften. Funktionale Fehler
stehen in den nummerierten Issues.

## Was schon gut funktioniert

- **Große Tap-Ziele** (Kacheln ca. 168 px hoch, Ergebnisbuttons 200 px). Das
  passt zum Nutzungskontext am Tisch.
- **Stand oben fixiert**, große Tabellenziffern, Vorzeichen immer sichtbar,
  Farbe nur als zweiter Kanal.
- **Sitzring** beim neuen Abend: ein gutes, eigenständiges Motiv, das man
  weiter ausbauen kann.
- **Berechneter Spielwert groß über den Buttons** mit Rechenweg darunter.
- Die Ergebnisbuttons tragen die Punkte („Gewonnen +36“). Man sieht vor dem
  Tap, was passiert.

## Visuelle Identität

- Die App hat noch keine eigene Handschrift: Material-3-Standard in Dunkel
  mit Gold-Akzent, Titel „Rotaskat“ als Fließtext.
- **Farbsymbole fehlen.** Karo, Herz, Pik und Kreuz stehen nur als Wort auf
  den Kacheln. ♦ ♥ ♠ ♣ (Herz/Karo rot, Pik/Kreuz dunkel bzw. in der
  Turnierfarbe) erkennt man schneller als Text, gerade bei schlechtem Licht.
- Der **Auswahlzustand** (dunkles Oliv/Gold mit hellem Rand) wirkt matschig
  und hebt sich bei schlechtem Licht kaum von den unausgewählten Kacheln ab.
- Die **Ergebnisbuttons** in Pastellgrün/-rot passen farblich nicht zum
  Gold-Akzent. Im deaktivierten Zustand sind sie fast unsichtbar, man sieht
  nicht, dass dort gleich etwas kommt.
- Die **Snackbar** ist hell auf dunklem Grund und liegt über den
  Ergebnisbuttons (siehe #009). Sie ist das auffälligste Element des
  Bildschirms, zur falschen Zeit.

## Hierarchie und Dichte

- Der Abend-Bildschirm ist zu hoch für einen Bildschirm (siehe #003). Der
  Stand braucht mit Name, Zahl und „gibt“ drei Zeilen, die Geberzeile eine
  eigene Zeile, die Wertkarte bis zu vier Zeilen.
- Viele **erklärende Absätze in der Oberfläche**, die wie Entwickler-Kommentare
  klingen: „Jede Quote steht mit der Anzahl dahinter. Ohne sie ist sie nicht zu
  lesen.“, „Waagerecht die Runden, senkrecht der laufende Stand in Punkten. Die
  Nulllinie ist staerker gezeichnet.“, „Gezaehlt werden alle nicht geloeschten
  Runden …“. Kürzen, in ein Info-Symbol verschieben oder ganz streichen.
- Übersichtskarten: Die Führungszahl rechts (+50) steht ohne Namen da. Man
  muss unten in der Zeile suchen, wer führt.

## Navigation und Bedienmuster

- **Textbuttons statt Icons** in der Kopfzeile („Zurueck“, „Rangliste“,
  „Statistik“, „Abend beenden“, „Abrechnung“, „Verlauf“). Das entspricht nicht
  den Android-Konventionen (Pfeil zurück, Icons, Überlauf-Menü) und kostet
  Platz.
- Der laufende Abend hat oben keinen Zurück-Pfeil, man kommt nur über die
  System-Geste zurück.
- „Abend beenden“ steht als gleichwertiger Textbutton oben rechts und ist
  damit gut erreichbar, obwohl es die folgenreichste Aktion ist.
- Die **Übergänge zwischen Bildschirmen** sind Crossfades mit gut 0,5–1 s.
  Das fühlt sich träge an. Während des Übergangs gehen Taps verloren, das ist
  im Test mehrfach passiert.
- Kein Einstellungsbereich (siehe #002). Für das Design heißt das: Es braucht
  einen Ort in der Informationsarchitektur.

## Diagramm (Punkteverlauf)

- Dünne Linien, Strichmuster statt klarer Farben. Lars' gepunktete Linie in
  Hellviolett ist auf Dunkelgrau kaum zu sehen.
- Die Legende steht getrennt unter dem Diagramm. Namen direkt am Linienende
  wären schneller zu lesen.
- Die x-Achse („0 … 5“) hat keine Beschriftung.

## Barrierefreiheit (aus dem Accessibility-Baum)

- Die Auswahlkacheln (Alleinspieler, Spielart, Spitzen, Geber) melden weder
  `selected` noch eine Rolle. TalkBack kann nicht vorlesen, was gewählt ist.
  Lösung: `Modifier.selectable(selected, role = Role.RadioButton)` bzw.
  `semantics { selected = … }`.
- Der Schalter „Ueberreizt“ meldet seinen Zustand korrekt.
- Die Kontraste von deaktivierten Elementen und Hinweistexten sollten beim
  Redesign gegen WCAG AA geprüft werden (Tipp: Skill
  `design:accessibility-review`).

## Offene Fragen für die Design-Runde

- Hell oder dunkel als Standard? In der Kneipe ist Dunkel sinnvoll. Ein
  „Kneipenmodus“ mit maximalem Kontrast wäre eine eigene Variante.
- Querformat: nicht getestet. Das Handy liegt am Tisch oft quer oder flach.
- Tablet-Layout: nicht getestet.
