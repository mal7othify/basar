package com.mal7othify.basar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Landing screen: one card per business use case, each opening a demo of how
 * the library serves it.
 */
@Composable
fun HomeScreen(onOpen: (DemoScreen) -> Unit) {
    val demos =
        listOf(
            DemoScreen.FURNITURE,
            DemoScreen.MEASURE,
            DemoScreen.SOLAR_SYSTEM,
            DemoScreen.SHOWCASE,
        )

    LazyColumn(
        modifier = Modifier.fillMaxWidth().systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text("Basar — Spatial-KMP", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "One Kotlin scene API, rendered by ARCore, ARKit, WebXR — " +
                        "or the in-browser preview when no AR hardware is around.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(demos) { demo ->
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(demo) },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(demo.title, style = MaterialTheme.typography.titleMedium)
                    Text(demo.tagline, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text(
                "On desktop browsers the demos run in the non-XR preview: " +
                    "drag to orbit, right-drag to pan, scroll to zoom, click to tap.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
