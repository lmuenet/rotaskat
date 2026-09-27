---
titel: App stürzt beim ersten Tap in der Rundeneingabe ab (VIBRATE-Berechtigung fehlt)
typ: bug
schwere: kritisch
bereich: Rundeneingabe, Haptik
status: erledigt
gefunden: 2026-09-27, Pixel 10 Pro, Android 17, Debug-Build von main (6abea4c)
---

## Beobachtung

Im laufenden Abend führt der erste Tap auf eine Auswahl (Alleinspieler,
Spielart, …) sofort zum Absturz. Am Tisch lässt sich damit **keine einzige
Runde eintragen**.

```
E/AndroidRuntime: FATAL EXCEPTION: main
java.lang.SecurityException: vibrate: Neither user 10541 nor current process has android.permission.VIBRATE.
    at android.os.Vibrator.vibrate(Vibrator.java:552)
    at io.rotaskat.app.ui.common.RotaskatHaptics.play(Haptics.kt:46)
    at io.rotaskat.app.ui.common.RotaskatHaptics.select(Haptics.kt:34)
    at io.rotaskat.app.ui.round.RoundEntryPanelKt.RoundEntryPanel$pick(RoundEntryPanel.kt:75)
```

## Reproduktion

1. Laufenden Abend öffnen.
2. Einen Alleinspieler antippen → App schließt sich.

## Ursache

`RotaskatHaptics.play()` ruft `Vibrator.vibrate()` auf, im
`app/src/main/AndroidManifest.xml` steht aber nur `INTERNET` und
`ACCESS_NETWORK_STATE`. `hasVibrator()` prüft nur die Hardware, nicht die
Berechtigung.

## Vorschlag

- `<uses-permission android:name="android.permission.VIBRATE" />` ins Manifest
  (normale Berechtigung, keine Laufzeitabfrage nötig). Für den Funktionstest
  ist das lokal bereits eingetragen, aber nicht committet.
- Zusätzlich `play()` defensiv machen (`try { … } catch (SecurityException)`),
  damit Haptik als Zusatzkanal nie die Eingabe mitreißt.
- Einen Robolectric- oder Instrumented-Test, der `select()`/`commit()` mit dem
  echten Manifest aufruft. `assembleDebug` und die Unit-Tests haben den Fehler
  nicht gefunden, weil in Vorschau und Tests `RotaskatHaptics.None` greift.
