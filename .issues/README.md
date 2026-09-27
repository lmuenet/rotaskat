# Issues

Lokale Issue-Sammlung. Angelegt nach dem ersten Funktionstest auf einem echten
Gerät (27.09.2026, Pixel 10 Pro / Android 17, Debug-Build von `main` @ 6abea4c,
gesteuert über mobile-mcp und adb).

| Nr. | Titel | Typ | Schwere |
|-----|-------|-----|---------|
| [001](001-absturz-vibrate-permission.md) | App stürzt beim ersten Tap ab (VIBRATE fehlt) | bug | **kritisch** |
| [002](002-keine-einstellungen-kader-cent-verein.md) | Kein Weg zu Kader, Cent-Satz, Vereinsbeitritt | fehlende Funktion | hoch |
| [003](003-vier-tap-pfad-scrollen.md) | Vier-Tap-Pfad bricht: Spitzen unter der Falz, Scrollposition | bug / UX | hoch |
| [004](004-ueberreizt-gebot-vorgabe.md) | Überreizt: Gebot startet bei 18, „Gewonnen“ aktiv | bug | mittel |
| [005](005-ramsch-summe-und-eingabe.md) | Ramsch: Speichern trotz Summe ≠ 120, Tastatur | bug | mittel |
| [006](006-lokaler-modus-zeigt-sync.md) | Lokaler Modus zeigt „wartet auf Sync“ | bug | mittel |
| [007](007-geloeschte-runden-in-liste.md) | Gelöschte Runden bleiben sichtbar, Nummernlücken | UX | mittel |
| [008](008-beendeter-abend-sackgasse.md) | Beendeter Abend: leerer Korrekturbildschirm | bug | mittel |
| [009](009-rueckgaengig-und-loeschen.md) | Rückgängig-Leiste zu kurz, verdeckt Buttons | UX | mittel |
| [010](010-kleinere-eingabefehler.md) | Kleinere Eingabe-Unstimmigkeiten | UX | niedrig |
| [011](011-texte-umlaute-grammatik.md) | Texte: Umlaute, „1 Abende“, Gleichstand | Text | niedrig |
| [012](012-leere-abende.md) | Leere Abende lassen sich nicht verwerfen | UX | niedrig |
| [013](013-funktionsideen.md) | Funktionsideen | idee | – |

Look & Feel für die spätere Design-Runde: [DESIGN-NOTIZEN.md](DESIGN-NOTIZEN.md)

## Was geprüft wurde und stimmt

Nachgerechnet und korrekt: Farbspiel und Grand mit Spitzen, Ouvert inklusive
automatisch gesetzter Stufen (Kreuz mit 2 Ouvert = 108), Überreizt nach
SCORING.md (Karo mit 1, gereizt 20 → 27), Null (23, halbe Punkte als „,5“),
Ramsch-Verteilung, Korrektur einer Runde, Löschen, Rückgängig, Rotation des
Gebers am Vierertisch, Geber manuell ändern, Grand auf 1–4 Spitzen begrenzt,
Geldabrechnung mit minimalen Zahlungen und Restcent-Verteilung, Punkteverlauf,
Rangliste (alle Jahre / Saison), Spielerstatistik, neuer Abend zu dritt.

Nicht geprüft: Vereinsmodus und Sync gegen einen Server, Kontra/Re in der
Abrechnung, Ramsch-Gleichstand und Jungfrau/Schieben/Durchmarsch,
`KeepScreenOn`, Querformat, Prozess-Tod mitten in der Eingabe.
