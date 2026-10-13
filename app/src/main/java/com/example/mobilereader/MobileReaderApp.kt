package com.example.mobilereader

import android.app.Application
import android.os.Build
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.svg.SvgDecoder
import com.example.mobilereader.data.BookRepository
import com.example.mobilereader.data.SettingsRepository

class MobileReaderApp : Application(), SingletonImageLoader.Factory {
    val bookRepository: BookRepository by lazy { BookRepository(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate() {
        super.onCreate()
        ComposeFoundationFlags.isNewContextMenuEnabled = false
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
}
