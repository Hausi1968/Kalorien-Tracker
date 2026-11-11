@file:OptIn(ExperimentalMaterial3Api::class)

package de.kh.kalorientracker.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import de.kh.kalorientracker.R

/**
 * Gerüst jeder Seite: TopBar (mit Zurück, falls gesetzt), Snackbar, FAB.
 * Unten kümmert sich das äußere Gerüst um Navigationsleiste und Systemleiste,
 * daher hier standardmäßig keine eigenen Insets.
 */
@Composable
fun Seite(
    titel: String,
    onZurueck: (() -> Unit)? = null,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    aktionen: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    insets: WindowInsets = WindowInsets(0, 0, 0, 0),
    titelZeile: (@Composable () -> Unit)? = null,
    inhalt: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (titelZeile != null) titelZeile()
                    else Text(titel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (onZurueck != null) {
                        IconButton(onClick = onZurueck) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.zurueck))
                        }
                    }
                },
                actions = aktionen,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = fab,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = insets,
        content = inhalt,
    )
}
