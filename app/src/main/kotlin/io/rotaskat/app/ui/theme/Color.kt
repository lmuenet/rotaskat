package io.rotaskat.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Die Palette der App: "Kartentisch".
 *
 * Es gibt bewusst nur ein dunkles Schema und keinen Hellmodus. Gespielt wird in
 * einer Kneipe mit schlechtem Licht; eine helle Flaeche auf dem Tisch blendet
 * die ganze Runde und zwingt das Auge nach jedem Blick auf die Karten neu zur
 * Anpassung. Der Hellmodus waere kein Zugewinn, sondern eine Fehlbedienung mit
 * Umschalter davor.
 *
 * Die Grundflaeche ist warmes Anthrazit und bewusst NICHT reines Schwarz: auf
 * OLED-Displays laesst ein #000000-Grund Kanten und Textraender sichtbar
 * schmieren, und die Erhoehung einer Kachel gegen den Hintergrund waere nicht
 * mehr darstellbar. Warm statt der violettstichigen Material-Neutraltoene, weil
 * Gold und Elfenbein darauf wie Karten auf einem Tisch stehen statt wie
 * Leuchtschrift auf einem Bildschirm.
 */
private val Surface = Color(0xFF16140F)

// Warmes Gold als Leitfarbe. Bewusst weder gruen noch rot: beide Toene sind fuer
// Gewinn und Verlust reserviert und duerfen an keiner anderen Stelle der
// Oberflaeche auftauchen, sonst verliert das Signal seine Bedeutung.
private val Gold = Color(0xFFF2C46B)

// Elfenbein: Haupttext und die "schwarzen" Farben Pik und Kreuz.
private val Ivory = Color(0xFFEDE6D8)

/**
 * Das Farbschema. Handverlesen statt aus Dynamic Color abgeleitet - siehe
 * [RotaskatScoreColors] fuer die Begruendung, die fuer die Leitfarbe genauso
 * gilt: der Wiedererkennungswert einer App, die je nach Hintergrundbild anders
 * aussieht, ist null.
 */
val RotaskatColorScheme: ColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF2A1F00),
    // Der Goldschimmer einer gewaehlten Kachel. Dunkel genug, dass der
    // Goldrand darauf der staerkere Kanal bleibt.
    primaryContainer = Color(0xFF3A2F14),
    onPrimaryContainer = Color(0xFFFFE7B0),
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
    onBackground = Ivory,
    surface = Surface,
    onSurface = Ivory,
    surfaceVariant = Color(0xFF39342C),
    onSurfaceVariant = Color(0xFFC9BFAE),
    surfaceTint = Gold,

    surfaceContainerLowest = Color(0xFF100E0A),
    // Die untere Navigationsleiste.
    surfaceContainerLow = Color(0xFF1E1B16),
    // Kacheln, Karten, die Zusaetze-Zeile.
    surfaceContainer = Color(0xFF221F19),
    // Menue, Dialoge, Sheet, Undo-Zeile.
    surfaceContainerHigh = Color(0xFF2E2A23),
    surfaceContainerHighest = Color(0xFF39342C),

    outline = Color(0xFF8F8676),
    // Der Rand einer nicht gewaehlten Kachel.
    outlineVariant = Color(0xFF3A352C),

    inverseSurface = Ivory,
    inverseOnSurface = Color(0xFF2E2A23),
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
 *
 * [gainContainer] und [lossContainer] sind die Flaechen von "Gewonnen" und
 * "Verloren": satt und dunkel statt pastellig, damit sie neben dem Gold nicht
 * wie Fremdkoerper wirken und trotzdem die groessten Flaechen des Bildschirms
 * bleiben.
 */
@Immutable
data class RotaskatScoreColors(
    val gain: Color,
    val onGain: Color,
    val gainContainer: Color,
    val onGainContainer: Color,
    val loss: Color,
    val onLoss: Color,
    val lossContainer: Color,
    val onLossContainer: Color,
    /** Genau null Punkte. Bewusst weder gruen noch rot. */
    val neutral: Color,
    /** Der Aussetzende: sichtbar vorhanden, aber ohne Beteiligung. */
    val sittingOut: Color,
)

val RotaskatScoreColorsDark = RotaskatScoreColors(
    gain = Color(0xFF6FD08C),
    onGain = Color(0xFF00391B),
    gainContainer = Color(0xFF1E5A37),
    onGainContainer = Color(0xFFE3F7E8),
    loss = Color(0xFFFF8A80),
    onLoss = Color(0xFF5C0007),
    lossContainer = Color(0xFF6E2320),
    onLossContainer = Color(0xFFFFE3E0),
    neutral = Color(0xFFB6AFBC),
    sittingOut = Color(0xFF7A737F),
)

val LocalScoreColors = staticCompositionLocalOf { RotaskatScoreColorsDark }

/**
 * Farben, die weder zum Material-Schema noch zu Gewinn/Verlust gehoeren.
 *
 * [suitRed] ist Kupfer, nicht Rot: Karo und Herz sollen sich von Pik und Kreuz
 * so unterscheiden wie auf dem Kartenblatt, ohne dass die Farbe des Verlusts
 * eine zweite Bedeutung bekommt. Kupfer steht deshalb nie an einer Zahl.
 */
@Immutable
data class RotaskatAccentColors(
    /** Karo und Herz. */
    val suitRed: Color,
    /** Pik und Kreuz. */
    val suitBlack: Color,
    /** Abschnittslabels, Hinweise, inaktive Ziele der unteren Leiste. */
    val labelMuted: Color,
    /** Gestrichelter Umriss eines Ergebnisbuttons, der noch nicht bereit ist. */
    val disabledOutline: Color,
)

val RotaskatAccentColorsDark = RotaskatAccentColors(
    suitRed = Color(0xFFDB8350),
    suitBlack = Ivory,
    labelMuted = Color(0xFF9E9483),
    disabledOutline = Color(0xFF7A7060),
)

val LocalAccentColors = staticCompositionLocalOf { RotaskatAccentColorsDark }
