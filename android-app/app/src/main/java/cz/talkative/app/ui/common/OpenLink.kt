package cz.talkative.app.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import cz.talkative.app.R

/** Otevře [url] v prohlížeči; když v zařízení žádný není, jen to oznámí (aplikace nespadne). */
fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.about_link_failed, Toast.LENGTH_LONG).show()
    }
}
