package io.github.rvonbergen84.tvlauncher

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import okhttp3.OkHttpClient

class LauncherApp : Application(), ImageLoaderFactory {

    /** Image loader for background photos: big disk cache, and a User-Agent Wikimedia accepts. */
    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "TVLauncher/1 (personal TV home screen; https://github.com/rvonbergen84/tv-launcher)")
                        .build(),
                )
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("photos"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false) // Commons images never change; keep them cached.
            .build()
    }
}
