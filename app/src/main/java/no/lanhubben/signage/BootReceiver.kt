package no.lanhubben.signage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Forsøker å starte appen ved oppstart. På nyere Android kan systemet blokkere dette.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in STARTUP_ACTIONS) {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private companion object {
        val STARTUP_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
