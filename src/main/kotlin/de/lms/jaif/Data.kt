package de.lms.jaif

import java.awt.Dimension
import java.awt.Point
import java.awt.image.BufferedImage

public data class GifData(
    val gifSize: Dimension,
    val frames: List<GifFrame>,
    val loopMode: GifLoopMode = GifLoopMode.LoopInfinitely,
    val colorResolution: GifColorResolution? = null,
)

@JvmInline
public value class GifColorResolution(public val value: Int) {
    init {
        require(value in 1..8)
    }
}

public data class GifFrame(
    val image: BufferedImage,
    val durationInMs: Int,
    val disposalMethod: GifFrameDisposalMethod = GifFrameDisposalMethod.None,
    val drawOffset: Point = Point(0, 0),
)

public enum class GifFrameDisposalMethod(internal val metadataValue: String) {
    None("none"),
    DoNotDispose("doNotDispose"),
    RestoreToBackgroundColor("restoreToBackgroundColor"),
    RestoreToPrevious("restoreToPrevious"),
}

public sealed class GifLoopMode(internal val loopCount: Int) {
    public data object LoopInfinitely : GifLoopMode(0)
    public class LoopTimes(loopCount: Int) : GifLoopMode(loopCount)

    public companion object {
        internal fun fromLoopCount(loopCount: Int): GifLoopMode {
            return if (loopCount == 0) LoopInfinitely else LoopTimes(loopCount)
        }
    }
}
