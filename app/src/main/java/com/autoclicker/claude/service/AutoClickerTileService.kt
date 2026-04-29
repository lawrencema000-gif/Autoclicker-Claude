package com.autoclicker.claude.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.autoclicker.claude.data.CommandBus
import com.autoclicker.claude.data.RunState
import com.autoclicker.claude.data.TapCommand

/**
 * Quick Settings tile that toggles the last-used profile from the lockscreen
 * or notification shade. Tap once: starts last profile (or restarts current).
 * Tap while running: stops.
 */
@RequiresApi(Build.VERSION_CODES.N)
class AutoClickerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        when (CommandBus.runState.value) {
            RunState.RUNNING, RunState.PAUSED -> CommandBus.send(TapCommand.Stop)
            RunState.IDLE -> CommandBus.send(TapCommand.RestartLastProfile)
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val running = CommandBus.runState.value != RunState.IDLE
        val hasProfile = CommandBus.lastProfile.value != null
        tile.state = when {
            running -> Tile.STATE_ACTIVE
            hasProfile -> Tile.STATE_INACTIVE
            else -> Tile.STATE_UNAVAILABLE
        }
        tile.label = "Auto Clicker"
        tile.contentDescription = when {
            running -> "Stop Auto Clicker"
            hasProfile -> "Start ${CommandBus.lastProfile.value?.name ?: "last script"}"
            else -> "Auto Clicker (no saved scripts)"
        }
        tile.updateTile()
    }
}
