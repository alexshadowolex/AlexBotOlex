package commands

import Command

val soundAlertCommand: Command = Command (
    names = listOf("sa", "soundalert"),
    hasGlobalCooldown = true,
    handler = {

    }
)