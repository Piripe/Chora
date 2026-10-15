package com.craftworks.music.utils

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.craftworks.music.R

object ShareUtils {
    /** Opens the system share sheet with [url]. Returns false if no app can receive it (on some TVs). */
    fun shareLink(context: Context, url: String, title: String?): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            title?.let { putExtra(Intent.EXTRA_TITLE, it) }
        }
        return try {
            context.startActivity(
                Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun copyLink(context: Context, url: String, title: String?) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(title ?: url, url))
        // Android 13+ shows its own confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            Toast.makeText(context, R.string.share_link_copied, Toast.LENGTH_SHORT).show()
    }
}
