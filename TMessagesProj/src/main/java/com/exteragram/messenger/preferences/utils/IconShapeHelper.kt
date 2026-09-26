package com.exteragram.messenger.preferences.utils

import android.content.res.Resources
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.text.TextUtils
import androidx.core.graphics.PathParser
import com.exteragram.messenger.ExteraConfig
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import kotlin.math.max
import kotlin.math.min

object IconShapeHelper {

    private var cacheInitialized = false
    private var cachedSystemPath: Path? = null
    private var isSystemPathSquare = false
    private val scratchRect = RectF()
    private val scratchMatrix = Matrix()

    fun getFinalIconShapePath(width: Float, height: Float, cornerRadius: Float): Path {
        val useSystemShape = Build.VERSION.SDK_INT >= 26 && ExteraConfig.useSystemIconShape
        if (useSystemShape && !cacheInitialized) {
            initSystemPathCache()
        }
        val sourcePath = (if (useSystemShape) cachedSystemPath else null) ?: getDefaultPath()

        val w = AndroidUtilities.dpf2(width)
        val h = AndroidUtilities.dpf2(height)
        val radius = AndroidUtilities.dpf2(cornerRadius)
        if (radius > 0f && useSystemShape && isSystemPathSquare) {
            scratchRect.set(0f, 0f, w, h)
            return Path().apply { addRoundRect(scratchRect, radius, radius, Path.Direction.CW) }
        }
        return resizePath(sourcePath, w, h)
    }

    private fun resizePath(path: Path?, width: Float, height: Float): Path {
        val result = Path()
        if (path != null && !path.isEmpty && width > 0f && height > 0f) {
            path.computeBounds(scratchRect, true)
            if (scratchRect.width() > 0f && scratchRect.height() > 0f) {
                scratchMatrix.reset()
                scratchMatrix.setRectToRect(scratchRect, RectF(0f, 0f, width, height), Matrix.ScaleToFit.FILL)
                path.transform(scratchMatrix, result)
            }
        }
        return result
    }

    private fun getDefaultPath(): Path =
        PathParser.createPathFromPathData("M50,0A50,50,0,0,1,50,100A50,50,0,0,1,50,0")

    private fun initSystemPathCache() {
        try {
            var path: Path? = null
            if (Build.VERSION.SDK_INT >= 26) {
                val drawable = ColorDrawable(0)
                val iconMask = AdaptiveIconDrawable(drawable, drawable).iconMask
                if (iconMask != null && !iconMask.isEmpty) {
                    path = Path(iconMask)
                }
            }
            if (path == null) {
                val system = Resources.getSystem()
                val identifier = system.getIdentifier("config_icon_mask", "string", "android")
                if (identifier != 0) {
                    val pathData = system.getString(identifier)
                    if (!TextUtils.isEmpty(pathData)) {
                        path = PathParser.createPathFromPathData(pathData)
                    }
                }
            }
            if (path != null && !path.isEmpty) {
                cachedSystemPath = path
                val bounds = RectF()
                path.computeBounds(bounds, true)
                isSystemPathSquare = calculateIfShouldUseRoundedRect(path, bounds)
            } else {
                cachedSystemPath = null
                isSystemPathSquare = false
            }
        } catch (e: Exception) {
            FileLog.e(e)
            cachedSystemPath = null
            isSystemPathSquare = false
        } finally {
            cacheInitialized = true
        }
    }

    private fun calculateIfShouldUseRoundedRect(path: Path, bounds: RectF): Boolean {
        if (path.isEmpty) return false
        val clip = Region(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        val region = Region().apply { setPath(path, clip) }
        if (region.isRect) return true
        return hasSharpCorners(path, bounds)
    }

    private fun hasSharpCorners(path: Path, bounds: RectF): Boolean {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return false
        val size = max(3, (min(bounds.width(), bounds.height()) * 0.1f).toInt())
        val region = Region().apply {
            setPath(path, Region(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()))
        }
        val left = bounds.left.toInt()
        val top = bounds.top.toInt()
        val right = bounds.right.toInt()
        val bottom = bounds.bottom.toInt()
        val corners = arrayOf(
            Rect(left, top, left + size, top + size),
            Rect(right - size, top, right, top + size),
            Rect(right - size, bottom - size, right, bottom),
            Rect(left, bottom - size, left + size, bottom)
        )
        val scratch = Region()
        for (corner in corners) {
            scratch.set(region)
            if (!scratch.op(corner, Region.Op.INTERSECT)) {
                return false
            }
        }
        return true
    }
}
