package com.mal7othify.basar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun ArCubeContent(
    onStatus: (String) -> Unit,
    modifier: Modifier,
) {
    LaunchedEffect(Unit) { onStatus("iOS AR arrives with the R2 ARKit adapter") }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            "The v2 ARKit tracking adapter lands in milestone R2 —\n" +
                "try the Filament Spike screen meanwhile.",
            modifier = Modifier.padding(24.dp),
        )
    }
}
