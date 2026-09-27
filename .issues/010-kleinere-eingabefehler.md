---
titel: Kleinere Eingabe-Unstimmigkeiten (Korrektur, Null, Geber)
typ: UX
schwere: niedrig
bereich: Rundeneingabe
status: erledigt
gefunden: 2026-09-27
---

Sammel-Issue für kleine Dinge, die beim Durchspielen aufgefallen sind.

## Korrekturmodus zeigt das ursprüngliche Ergebnis nicht

Beim Öffnen einer gespielten Runde sind Alleinspieler, Spielart und Spitzen
vorbelegt, aber weder „Gewonnen“ noch „Verloren“ ist markiert. Man sieht nicht,
was ursprünglich gespeichert war. Vorschlag: den gespeicherten Ausgang markieren,
z. B. mit Rahmen und „bisher“.

## Null: irreführender Hinweis

Nach Alleinspieler und „Null“ steht in der Wertkarte weiterhin
„Alleinspieler und Spielart waehlen“, obwohl beides gewählt ist. Es fehlt die
Null-Variante, und die Kacheln liegen unter der Falz. Vorschlag: Hinweis
„Null-Variante wählen“ und „Null 23“ vorauswählen. Damit wäre Null wirklich der
Zwei-Tap-Sonderweg aus SCOPE.md.

## Geber ändern braucht zwei Taps

„aendern“ klappt die Geberauswahl auf, danach braucht es noch „fertig“. Die
Auswahl eines Gebers kann die Leiste direkt wieder schließen.

## Kurzer Zwischenzustand nach dem Speichern

Direkt nach „Gewonnen“ bleibt die Spielart für etwa eine Sekunde markiert,
während Wert und Buttons schon zurückgesetzt sind („-“ plus „Karo mit 1 = 18“).
Wer schnell weitertippt, sieht einen widersprüchlichen Zustand. Vorschlag:
Entwurf und Anzeige in einem Schritt zurücksetzen.

## Überreizt-Runde ohne Gebot in der Liste

In der Rundenliste steht „Karo mit 1 · Alex - ueberreizt“, aber nicht, bis wohin
gereizt wurde. Beim späteren Nachvollziehen fehlt genau diese Zahl.

## Umsetzung

- Korrektur: der gespeicherte Ausgang trägt einen Rahmen und „· bisher“
  (`RoundDraft.originalWon`).
- Null: „Null 23“ ist vorgewählt, der Hinweis nennt fehlende Angaben korrekt
  (`RoundDraft.missingHint`, kam mit #003).
- Geber: die Auswahl sitzt jetzt im Kopf der Spielerauswahl, ein Tap auf den
  Geber schließt sie (#003). Auf dem Gerät gegenprüfen.
- Zwischenzustand nach dem Speichern: Entwurf wechselt in einem Schritt (#009).
- Überreizt-Runde zeigt das Gebot in der Liste (#007).
