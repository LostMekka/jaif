package de.lms.jaif

import de.lms.jaif.helper.attribute
import de.lms.jaif.helper.modifyChildNode
import de.lms.jaif.helper.getChildNodeOrNull
import de.lms.jaif.helper.lazyLogArg
import de.lms.jaif.helper.toPrettyXmlString
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.InputStream
import java.io.OutputStream
import javax.imageio.IIOImage
import kotlin.math.roundToInt
import org.slf4j.LoggerFactory
import java.awt.Dimension

private val logger = LoggerFactory.getLogger("de.lms.jaif.Gif")

public fun readGif(inputStream: InputStream): GifData {
    return GifReader(inputStream).use { reader ->
        val streamMetadata = reader.getStreamMetadata()
        val gifSize = streamMetadata
            .getChildNodeOrNull("LogicalScreenDescriptor")
            ?.run {
                val w = getAttribute("logicalScreenWidth").toIntOrNull()
                val h = getAttribute("logicalScreenHeight").toIntOrNull()
                if (w != null && h != null) Dimension(w, h) else null
            }
            ?: error("Cannot read gif LogicalScreenDescriptor")
        logger.trace("stream metadata read:\n{}", lazyLogArg { streamMetadata.toPrettyXmlString() })
        val gifBounds = gifSize.asRectangle()

        val frames = mutableListOf<GifFrame>()
        val loopCounts = mutableMapOf<Int, Int>()
        for (i in 0 until reader.imageReader.getNumImages(true)) {
            val frameMetadata = reader.getFrameMetadata(i)

            var durationInMs = 100
            var disposalMethod = GifFrameDisposalMethod.None
            frameMetadata
                .getChildNodeOrNull("GraphicControlExtension")
                ?.apply {
                    durationInMs = (getAttribute("delayTime").toIntOrNull() ?: 10) * 10
                    disposalMethod = when (getAttribute("disposalMethod")) {
                        "doNotDispose" -> GifFrameDisposalMethod.DoNotDispose
                        "restoreToBackgroundColor" -> GifFrameDisposalMethod.RestoreToBackgroundColor
                        "restoreToPrevious" -> GifFrameDisposalMethod.RestoreToPrevious
                        else -> GifFrameDisposalMethod.None
                    }
                }
                ?: logger.warn("GraphicControlExtension not found for frame $i. frame duration and disposal method set to default.")
            logger.trace("frame $i metadata read:\n{}", lazyLogArg { frameMetadata.toPrettyXmlString() })

            frameMetadata
                .getChildNodeOrNull("ApplicationExtensions")
                ?.getChildNodeOrNull("ApplicationExtension")
                ?.takeIf { it.getAttribute("applicationID") == "NETSCAPE" }
                ?.takeIf { it.getAttribute("authenticationCode") == "2.0" }
                ?.let { (it.userObject as? ByteArray)?.toLoopCount() }
                ?.also { loopCounts[i] = it }

            val imageRect = frameMetadata
                .getChildNodeOrNull("ImageDescriptor")
                ?.run {
                    val x = getAttribute("imageLeftPosition").toIntOrNull()
                    val y = getAttribute("imageTopPosition").toIntOrNull()
                    val w = getAttribute("imageWidth").toIntOrNull()
                    val h = getAttribute("imageHeight").toIntOrNull()
                    if (x != null && y != null && w != null && h != null) Rectangle(x, y, w, h) else null
                }
                ?: gifBounds

            // Read image (converted to BufferedImage with TYPE_INT_ARGB for consistency)
            val image = reader.imageReader.read(i)
            val imageSize = image.size
            if (imageSize != imageRect.size) {
                logger.warn("Frame $i dimensions from metadata ($imageRect) does not match read image size ($imageSize). read image might contain corrupt data.")
            }
            if (imageRect !in gifBounds) {
                logger.warn("Frame $i dimensions from metadata ($imageRect) is at least partially outside logical screen ($gifBounds). read image might contain corrupt data.")
            }
            // TODO: determine: do we really want to convert the image?
            //  maybe add an option so the user can decide? maybe also with normalization to virtual screen space
            val bufferedImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
            val g = bufferedImage.createGraphics()
            g.drawImage(image, 0, 0, null)
            g.dispose()

            frames += GifFrame(
                image = bufferedImage,
                durationInMs = durationInMs,
                disposalMethod = disposalMethod,
                drawOffset = Point(imageRect.x, imageRect.y),
            )
        }

        val observedLoopCounts = loopCounts.values.toSet()
        val loopCount = when {
            observedLoopCounts.isEmpty() -> {
                logger.warn("Cannot read gif loop count: Gif does not contain a valid NETSCAPE ApplicationExtension. defaulting to 0 (loop infinitely)")
                0
            }
            observedLoopCounts.size > 1 -> {
                val biggest = observedLoopCounts.max()
                logger.warn("Cannot read gif loop count: Gif contains conflicting NETSCAPE ApplicationExtensions. taking the maximum value of $biggest${if (biggest == 0) " (loop infinitely)" else ""}")
                biggest
            }
            else -> observedLoopCounts.first()
        }

        GifData(gifSize, frames, GifLoopMode.fromLoopCount(loopCount))
    }
}

// metadata documentation: https://docs.oracle.com/javase/7/docs/api/javax/imageio/metadata/doc-files/gif_metadata.html
public fun writeGif(gifData: GifData, outputStream: OutputStream) {
    val frames = gifData.frames
    val loopMode = gifData.loopMode
    require(frames.isNotEmpty()) { "GIF frames list must not be empty" }
    val logicalScreenRect = gifData.gifSize.asRectangle()
    require(logicalScreenRect.width in 0..0xFFFF) { "gif is too wide. got ${logicalScreenRect.width}, max is 0xFFFF" }
    require(logicalScreenRect.height in 0..0xFFFF) { "gif is too high. got ${logicalScreenRect.height}, max is 0xFFFF" }
    frames.forEachIndexed { i, frame ->
        val frameBounds = frame.image.size.asRectangle(frame.drawOffset)
        require(frameBounds in logicalScreenRect) { "bounds of frame $i ($frameBounds) is outside the logical screen ($logicalScreenRect)" }
    }

    GifWriter(outputStream).use { writer ->
        val streamMetadata = writer.createStreamMetadata {
            modifyChildNode("LogicalScreenDescriptor") {
                attribute("logicalScreenWidth", logicalScreenRect.width) // 0..0xFFFF, or empty string for default
                attribute("logicalScreenHeight", logicalScreenRect.height) // 0..0xFFFF, or empty string for default
                attribute("colorResolution", gifData.colorResolution?.value ?: "") // 1..8, or empty string for default
                attribute("pixelAspectRatio", 0) // 0..0xFF // If 0, indicates square pixels, else W/H = (value + 16)/64
            }
            logger.trace("stream metadata to write:\n{}", lazyLogArg { toPrettyXmlString() })
        }
        writer.imageWriter.prepareWriteSequence(streamMetadata)
        for ((i, frame) in frames.withIndex()) {
            val frameMetadata = writer.createFrameMetadata {
                modifyChildNode("GraphicControlExtension") {
                    // GraphicControlExtension holds animation timing
                    val frameDuration = (frame.durationInMs / 10.0).roundToInt().coerceAtLeast(1)
                    attribute("delayTime", frameDuration)
                    attribute("disposalMethod", frame.disposalMethod.metadataValue)
                    attribute("userInputFlag", "FALSE")
                    attribute("transparentColorFlag", "FALSE")
                    attribute("transparentColorIndex", 0)
                }
                modifyChildNode("ImageDescriptor") {
                    // ImageDescriptor holds image position attributes
                    attribute("imageLeftPosition", frame.drawOffset.x)
                    attribute("imageTopPosition", frame.drawOffset.y)
                    attribute("imageWidth", frame.image.width)
                    attribute("imageHeight", frame.image.height)
                    attribute("interlaceFlag", "FALSE")
                }
                modifyChildNode("ApplicationExtensions") {
                    // hacky Netscape ApplicationExtension, because the real world is very messy
                    modifyChildNode("ApplicationExtension") {
                        attribute("applicationID", "NETSCAPE")
                        attribute("authenticationCode", "2.0")
                        userObject = loopCountToByteArray(loopMode.loopCount)
                    }
                }
                logger.trace("frame $i metadata to write:\n{}", lazyLogArg { toPrettyXmlString() })
            }
            val imageWritable = IIOImage(frame.image, null, frameMetadata)
            writer.imageWriter.writeToSequence(imageWritable, writer.imageWriteParam)
        }
        writer.imageWriter.endWriteSequence()
    }
}

private val infiniteLoopBytes = byteArrayOf(1, 0, 0)
private fun loopCountToByteArray(loopCount: Int): ByteArray {
    if (loopCount > 0xFFFF) {
        logger.warn("loop count does not fit into two bytes. defaulting to 0 (loop infinitely)")
        return infiniteLoopBytes
    }
    if (loopCount < 0) {
        logger.warn("loop count is negative. defaulting to 0 (loop infinitely)")
        return infiniteLoopBytes
    }
    return byteArrayOf(1, (loopCount and 0xFF).toByte(), (loopCount shr 8 and 0xFF).toByte())
}

private fun ByteArray.toLoopCount(): Int? {
    if (size < 3 || this[0] != 1.toByte()) return null
    return this[1].toInt() + this[2].toInt() shl 8
}
