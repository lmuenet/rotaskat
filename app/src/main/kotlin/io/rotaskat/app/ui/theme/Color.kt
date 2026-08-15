package io.rotaskat.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Die Palette der App.
 *
 * Es gibt bewusst nur ein dunkles Schema und keinen Hellmodus. Gespielt wird in
 * einer Kneipe mit schlechtem Licht; eine helle Flaeche auf dem Tisch blendet
 * die ganze Runde und zwingt das Auge nach jedem Blick auf die Karten neu zur
 * Anpassung. Der Hellmodus waere kein Zugewinn, sondern eine Fehlbedienung mit
 * Umschalter davor.
 *
 * Die Grundflaeche ist bewusst NICHT reines Schwarz: auf OLED-Displays laesst
 * ein #000000-Grund Kanten und Textraender sichtbar schmieren, und die Erhoehung
 * einer Karte gegen den Hintergrund waere nicht mehr darstellbar.
 */
private val Surface = Color(0xFF141218)

// Warmes Gold als Leitfarbe. Bewusst weder gruen noch rot: beide Toene sind fuer
// Gewinn und Verlust reserviert und duerfen an keiner anderen Stelle der
// Oberflaeche auftauchen, sonst verliert das Signal seine Bedeutung.
private val Gold = Color(0xFFF2C46B)
private val GoldDark = Color(0xFF3D2E00)

/**
 * Das Farbschema. Handverlesen statt aus Dynamic Color abgeleitet - siehe
 * [RotaskatScoreColors] fuer die Begruendung, die fuer die Leitfarbe genauso
 * gilt: der Wiedererkennungswert einer App, die je nach Hintergrundbild anders
 * aussieht, ist null.
 */
val RotaskatColorScheme: ColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = GoldDark,
    primaryContainer = Color(0xFF574200),
    onPrimaryContainer = Color(0xFFFFDF9E),
    inversePrimary = Color(0xFF6F5B00),

    secondary = Color(0xFFCFC5B4),
    onSecondary = Color(0xFF36302A),
    secondaryContainer = Color(0xFF4D463D),
    onSecondaryContainer = Color(0xFFECE1CF),

    // Kuehler Akzent fuer alles Beilaeufige - Badges, Zaehler, Hinweise.
    tertiary = Color(0xFF9FCBE8),
    onTertiary = Color(0xFF003548),
    tertiaryContainer = Color(0xFF1F4C63),
    onTertiaryContainer = Color(0xFFC9E6FF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Surface,
    onBackground = Color(0xFFE7E0E8),
    surface = Surface,
    onSurface = Color(0xFFE7E0E8),
    surfaceVariant = Color(0xFF3A343F),
    onSurfaceVariant = Color(0xFFCCC3D0),
    surfaceTint = Gold,

    surfaceContainerLowest = Color(0xFF0E0C12),
    surfaceContainerLow = Color(0xFF1C1A21),
    surfaceContainer = Color(0xFF201E26),
    surfaceContainerHigh = Color(0xFF2B2831),
    surfaceContainerHighest = Color(0xFF36323C),

    outline = Color(0xFF958E9B),
    outlineVariant = Color(0xFF4A444F),

    inverseSurface = Color(0xFFE7E0E8),
    inverseOnSurface = Color(0xFF322F36),
    scrim = Color(0xFF000000),
)

/**
 * Gewinn und Verlust als feste Tokens.
 *
 * Sie stehen absichtlich neben dem [ColorScheme] und nicht darin: aus einem
 * Dynamic-Color-Schema abgeleitet waeren sie je nach Hintergrundbild des
 * Nutzers mal gruen, mal beige, mal violett. Eine Punktzahl, deren Farbe vom
 * Wallpaper abhaengt, ist als Signal wertlos.
 *
 * Die Farbe ist ohnehin nur der Zweitkanal. Traeger der Information ist das
 * immer mitgeschriebene Vorzeichen - siehe `formatPoints` -, damit die Tabelle
 * auch bei Rot-Gruen-Schwaeche und in der Kneipenbeleuchtung lesbar bleibt.
 */
@Immutable
data class RotaskatScoreColors(
    val gain: Color,
    val onGain: Color,
    val gainContainer: Color,
    val loss: Color,
    val onLoss: Color,
    val lossContainer: Color,
    /** Genau null Punkte. Bewusst weder gruen noch rot. */
    val neutral: Color,
    /** Der Aussetzende: sichtbar vorhanden, aber ohne Beteiligung. */
    val sittingOut: Color,
)

val RotaskatScoreColorsDark = RotaskatScoreColors(
    gain = Color(0xFF6FD08C),
    onGain = Color(0xFF00391B),
    gainContainer = Color(0xFF1B4D2E),
    loss = Color(0xFFFF8A80),
    onLoss = Color(0xFF5C0007),
    lossContainer = Color(0xFF6B2020),
    neutral = Color(0xFFB6AFBC),
    sittingOut = Color(0xFF7A737F),
)

val LocalScoreColors = staticCompositionLocalOf { RotaskatScoreColorsDark }
