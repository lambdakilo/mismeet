package app.mismeet.android.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.mismeet.android.AppModel

/** Brings background sharing back after a reboot, when the user had it on and the permissions are still there. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val model = AppModel.get(context)
        if (model.state.value.backgroundSharing &&
            LocationProvider.hasForegroundPermission(context) &&
            LocationProvider.hasBackgroundPermission(context)
        ) {
            LocationService.start(context)
        }
    }
}
