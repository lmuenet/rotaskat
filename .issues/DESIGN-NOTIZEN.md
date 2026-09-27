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

## Nachtrag nach der Fix-Runde (Gerätetest 27.09.2026, 12:40)

Funktional auf dem Pixel bestätigt: #001–#012 und „Abrechnung teilen“. Beim
Test gefunden und behoben: ein geleertes, automatisch ergänztes Ramsch-Feld
wurde sofort wieder aufgefüllt (Commit 50c3dce).

Für das Redesign aufgefallen:

- **Taps während der Übergänge landen auf dem alten Bildschirm.** Ein Tap aufs
  Zahnrad kurz nach „Zurück“ öffnete noch den Dialog „Abend beenden?“ des
  verlassenen Abends. Crossfades verkürzen oder Eingaben während der
  Transition sperren.
- **Abgeschnittene Namen:** „Joha… · gibt“ im Stand, „Gespeichert: Johannes ·
  K…“ in der Undo-Zeile. Stand und Undo-Zeile brauchen eine Form, in der
  Namen bis ~10 Zeichen ganz stehen.
- **Herleitung der Wertkarte** wird bei Ouvert nach drei Zeilen abgeschnitten
  – ausgerechnet „= 12 x 9 = 108“ fehlt. Kürzere Form, z. B. „Kreuz mit 2 ·
  Ouvert (inkl. Hand, Schneider, Schwarz) = 12 × 9“.
- **„durch die Ansage gesetzt“** ist auf „durch die“ gekürzt (zu schmale Kacheln).
- **Geberauswahl** klappt als zweite, fast identische Namensreihe direkt über
  der Alleinspieler-Reihe auf – verwechselbar. Eigene Form (Chips, Sheet,
  Sitzring) oder deutlich abgesetzt.
- **Undo-Zeile nach „Zurückgenommen“** wird ohne Knopf flacher, das Layout
  darunter springt.
- **Korrekturmodus:** Der Stand oben zeigt die aktuelle Rotation („Lars ·
  gibt“), die Runde hatte einen anderen Geber.
- **TalkBack:** Kacheln melden weiterhin kein `selected` (siehe oben).

## Stand nach Design-Runde Teil 1 (27.09.2026, Branch `design/kartentisch`)

Umgesetzt nach `docs/superpowers/specs/2026-09-27-design-kartentisch-design.md`
und auf dem Pixel 10 Pro geprüft (Screenshots lokal unter
`.superpowers/device-test/`).

Erledigt aus den Notizen oben:

- Eigene Handschrift „Kartentisch“: warmes Anthrazit, Gold, Barlow Semi
  Condensed für Namen/Zahlen/Titel.
- Farbsymbole als Vektor-Icons (Kupfer ♦♥, Elfenbein ♠♣) – als Textzeichen
  rendert Android sie als rote/graue Emoji.
- Auswahlzustand: Goldrand, Goldschimmer, Häkchen; Kacheln melden
  `selected`/Rolle an TalkBack (am Gerät als `checked` sichtbar).
- Ergebnisbuttons als satte Flächen, vorher als gestrichelter Umriss mit
  „+ ?“ sichtbar.
- Snackbar dunkel und oben über der Eingabe statt über den Buttons.
- Kopfzeilen mit Zurück-Pfeil und Icons; „Abend beenden …“ im Menü, abgesetzt.
- Untere Leiste Abende/Rangliste/Statistik; Übergänge 150/100 ms; Taps auf
  den verlassenen Bildschirm werden verschluckt (am Gerät geprüft).
- Stand: Namen ungekürzt, Geber in Gold mit Kartensymbol, Tap öffnet „Wer
  gibt?“ mit Sitzring; im Korrekturmodus der Geber der Runde.
- Herleitung kurz („Kreuz mit 2 · Ouvert = 12 × 9“), vollständig sichtbar.
- Undo-Zeile mit fester Höhe, „Gespeichert: Johannes · Kreuz mit 2“ passt.
- Startseite: laufender Abend als Karte mit Namen neben der Führungszahl,
  frühere Abende mit Sieger bzw. „Gleichstand“; „Neuer Abend“ nur ohne
  laufenden Abend (Anlegen/`startSession` verhindern keinen zweiten).

Offen für Teil 2 (Auswertung) und später:

- Diagramm, Rangliste, Statistik, Abrechnung (Textbuttons „Teilen“/„Verlauf“)
  und die erklärenden Absätze.
- Die untere Leiste springt beim Wechsel Abend ↔ Startseite ohne Animation
  ein/aus (am Gerät sichtbar, kosmetisch).
- Während der Abend lädt, zeigt die Kopfzeile kurz „Abend“ mit €-Symbol.
- Rand nicht gewählter Kacheln hat 1,5:1 Kontrast; die Kachel ist über ihre
  Beschriftung erkennbar, die Spec-Formulierung „3:1 für Ränder“ klären.
- Kleinigkeiten aus dem Gesamt-Review: toter Code (`toLeaderboard`,
  `toStats`, `selectedColor`, `sittingOut`), doppelte Routenliste,
  `selectableGroup` für Kachelreihen, „mehr“-Kachel als Radio statt Aufklapper,
  Sync-Hinweis einzeilig.
- Nicht geprüft: TalkBack-Sprachausgabe gehört (nur Semantik-Baum), Querformat,
  Tablet, große Schrift.

## Stand nach Design-Runde Teil 2 (27.09.2026)

Umgesetzt nach `docs/superpowers/specs/2026-09-27-design-auswertung-design.md`,
Tests grün, **auf dem Gerät noch nicht geprüft**. Beim nächsten Gerätetest
(App-Daten vorher sichern, danach zurückspielen) prüfen:

- Punkteverlauf mit 4 Spielern und Gleichstand am Ende: Namen am Linienende
  überdecken sich nicht, Verbindungslinien bei verschobenen Namen.
- Rangliste, Statistik (Chips, Kennzahl-Kacheln, ⓘ-Sheets), Abrechnung
  (Icons, Karten), Einstellungen, Beitritt.
- Untere Leiste beim Wechsel Abend ↔ Startseite: wächst mit, kein Sprung im
  Abend-Bildschirm.
- TalkBack: Kacheln als ein Fokusstopp, Überschriften als Überschrift.
