package com.exteragram.messenger.nowplaying.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.exteragram.messenger.api.dto.NowPlayingDTO
import com.exteragram.messenger.utils.ui.UIUtil
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLRPC
import java.util.concurrent.atomic.AtomicBoolean

data class NowPlayingCardData(
    val nowPlayingDTO: NowPlayingDTO,
    val backgroundColor: Int?,
    val accentColor: Int?,
    val coverBitmap: Bitmap?,
    val imageLocation: ImageLocation? = null,
    var userEmoji: Long = -1L
) {

    fun interface Callback {
        fun onDataLoaded(data: NowPlayingCardData)
    }

    companion object {

        @JvmStatic
        fun create(nowPlayingDTO: NowPlayingDTO, document: TLRPC.Document?, callback: Callback) {
            var location: ImageLocation? = null
            if (nowPlayingDTO.platform == "TELEGRAM" && document != null) {
                val photoSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 1000)
                if (photoSize != null) {
                    location = ImageLocation.getForDocument(photoSize, document)
                }
            }
            if (location == null && !nowPlayingDTO.coverUrl.isNullOrEmpty()) {
                location = ImageLocation.getForPath(nowPlayingDTO.coverUrl)
            }
            val imageLocation = location
            if (imageLocation == null) {
                AndroidUtilities.runOnUIThread {
                    callback.onDataLoaded(NowPlayingCardData(nowPlayingDTO, null, null, null, null))
                }
                return
            }

            val imageReceiver = ImageReceiver(null)
            val finished = AtomicBoolean(false)
            var timeoutRunnable: Runnable? = null

            fun finish(data: NowPlayingCardData) {
                if (finished.compareAndSet(false, true)) {
                    AndroidUtilities.cancelRunOnUIThread(timeoutRunnable)
                    imageReceiver.setDelegate(null)
                    imageReceiver.onDetachedFromWindow()
                    callback.onDataLoaded(data)
                }
            }

            timeoutRunnable = Runnable {
                finish(NowPlayingCardData(nowPlayingDTO, null, null, null, imageLocation))
            }

            AndroidUtilities.runOnUIThread {
                imageReceiver.onAttachedToWindow()
                imageReceiver.setDelegate { receiver, set, thumb, _ ->
                    if (!set || thumb) {
                        return@setDelegate
                    }
                    val bitmap = receiver.bitmap
                    Utilities.themeQueue.postRunnable {
                        val (background, accent) = extractColors(bitmap)
                        AndroidUtilities.runOnUIThread {
                            finish(NowPlayingCardData(nowPlayingDTO, background, accent, bitmap, imageLocation))
                        }
                    }
                }
                AndroidUtilities.runOnUIThread(timeoutRunnable, 15000L)
                imageReceiver.setImage(imageLocation, null, null, null, null, 0)
            }
        }

        private fun extractColors(bitmap: Bitmap?): Pair<Int?, Int?> {
            if (bitmap == null) {
                return null to null
            }
            val palette = Palette.from(bitmap).generate()
            var color = palette.darkVibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.darkMutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: AndroidUtilities.getDominantColor(bitmap)

            val contrast = ColorUtils.calculateContrast(Color.WHITE, color)
            if (contrast > 15.0) {
                color = UIUtil.adjustHsl(color, 2.0f)
            } else if (contrast < 10.0) {
                color = UIUtil.adjustHsl(color, 0.5f)
            }
            if (ColorUtils.calculateContrast(Color.WHITE, color) < 3.0) {
                color = ColorUtils.blendARGB(color, Color.BLACK, 0.3f)
            }

            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(color, hsl)
            val factor = when (hsl[2]) {
                in 0.0f..0.25f -> 2.0f
                in 0.25f..0.5f -> 1.5f
                in 0.5f..0.75f -> 1.0f
                else -> 0.5f
            }
            return color to UIUtil.adjustHsl(color, factor)
        }
    }
}
