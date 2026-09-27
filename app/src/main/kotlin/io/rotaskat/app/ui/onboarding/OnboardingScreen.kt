package io.rotaskat.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = 40.dp),
        )
        Text(
            text = "Punkte für eure Skatrunde.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = RotaskatDimens.sectionSpacing),
        )

        ChoiceCard(
            title = "Ohne Verein",
            body = "Spieler eintragen und loslegen. Alles bleibt auf dem Gerät – einem Verein " +
                "könnt ihr später beitreten, die Abende kommen mit.",
            onClick = onLocal,
        )

        ChoiceCard(
            title = "Mit Verein",
            body = "Mit Einladungscode. Die Abende landen auf eurem Server, die Rangliste " +
                "gilt für alle.",
            onClick = onJoin,
        )

        Text(
            text = "Am Tisch braucht die App keinen Empfang.",
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
        shape = RoundedCornerShape(RotaskatDimens.cardCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            // Grosszuegig, weil das die erste Beruehrung mit der App ist und
            // hier niemand zielen koennen muss.
            .heightIn(min = RotaskatDimens.bigTapTarget),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
