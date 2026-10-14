package com.craftworks.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.craftworks.music.managers.MediaProviderManager
import com.craftworks.music.managers.MigrationManager
import com.craftworks.music.player.PrefetchedArtworkInterceptor
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChoraApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        MigrationManager.init(this)
        MediaProviderManager.init(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(PrefetchedArtworkInterceptor()) }
            .build()
}
