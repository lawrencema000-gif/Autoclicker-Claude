package com.autoclicker.claude.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * @param onConfirm  Action for the confirm button. The dialog is then closed via onDismiss.
 * @param onDismiss  Closes the dialog. Also invoked on scrim tap / back press — these
 *                   must NOT carry a side effect (like disabling a feature); put any
 *                   cancel-only side effect in onCancel instead.
 * @param onCancel   Optional extra action for the dismiss (cancel) button only. Does not
 *                   run on scrim/back, so an accidental outside-tap can't trigger it.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    cancelLabel: String = "Cancel",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onCancel?.invoke(); onDismiss() }) { Text(cancelLabel) }
        }
    )
}
