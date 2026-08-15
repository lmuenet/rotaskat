package io.rotaskat.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.rotaskat.app.ui.theme.RotaskatDimens

/**
 * Der erste Bildschirm nach der Installation.
 *
 * Die Wahl steht bewusst gleichrangig da und nicht als "richtiger Weg" plus
 * "Notausgang". Ohne Verein zu spielen ist ein vollwertiger Betrieb: es fehlt
 * nur die vereinsweite Rangliste ueber mehrere Geraete, und das merkt man an
 * einem Abend zu viert an einem Tisch nicht.
 *
 * Der lokale Weg steht oben, weil er der wahrscheinlichere ist: einen Server
 * hat man selten schon aufgesetzt, wenn man die App zum ersten Mal oeffnet.
 */
@Composable
fun OnboardingScreen(
    onLocal: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(RotaskatDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(RotaskatDimens.itemSpacing),
    ) {
        Text(
            text = "Rotaskat",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 40.dp),
        )
        Text(
            text = "Punkte fuer eure Skatrunde. Wie soll es losgehen?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = RotaskatDimens.sectionSpacing),
        )

        ChoiceCard(
            title = "Ohne Verein",
            body = "Spieler eintragen und sofort loslegen. Alles bleibt auf diesem Geraet, " +
                "kein Server noetig. Ihr koennt spaeter jederzeit einem Verein beitreten - " +
                "die bereits gespielten Abende kommen dann mit.",
            onClick = onLocal,
        )

        ChoiceCard(
            title = "Mit Verein",
            body = "Ihr habt schon einen Rotaskat-Server und einen Einladungscode. " +
                "Die Abende landen dort und die Rangliste gilt fuer alle Mitglieder, " +
                "egal wer den Abend aufgeschrieben hat.",
            onClick = onJoin,
        )

        Text(
            text = "Gespielt und gerechnet wird in beiden Faellen offline. " +
                "Die App braucht am Tisch nie Empfang.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = RotaskatDimens.sectionSpacing),
        )
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            // Grosszuegig, weil das die erste Beruehrung mit der App ist und
            // hier niemand zielen koennen muss.
            .heightIn(min = RotaskatDimens.bigTapTarget),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
