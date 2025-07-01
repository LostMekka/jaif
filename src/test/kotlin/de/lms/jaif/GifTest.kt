package de.lms.jaif

import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GifTest {
    @Test
    fun testWriteGif() {
        val size = 500 by 300
        val gifData = GifData(
            gifSize = size,
            frames = listOf("#E40303", "#FF8C00", "#FFED00", "#008026", "#004CFF", "#732982").mapIndexed { i, color ->
                val yOffset = 50 * i
                val image = createColorImage(size.width by size.height - yOffset, color = color)
                GifFrame(image = image, durationInMs = 200, drawOffset = Point(0, yOffset))
            },
            loopMode = GifLoopMode.LoopInfinitely
        )

        val outputFile = File("test-output/testWriteGif-output.gif")
        FileOutputStream(outputFile).use { outputStream ->
            writeGif(gifData, outputStream)
        }

        assertTrue(outputFile.exists(), "Output file should exist")
        assertTrue(outputFile.length() > 0, "Output file should have content")
    }

    @Test
    fun testReadGif() {
        val size = 200 by 200
        val originalGifData = GifData(
            gifSize = size,
            frames = listOf(
                GifFrame(createColorImage(size, "#FF0000"), durationInMs = 200),
                GifFrame(createColorImage(size, "#00FF00"), durationInMs = 300),
                GifFrame(createColorImage(size, "#0000FF"), durationInMs = 400),
            ),
            loopMode = GifLoopMode.LoopInfinitely
        )

        val tempFile = File.createTempFile("test-gif", ".gif")
        tempFile.deleteOnExit()
        FileOutputStream(tempFile).use { outputStream ->
            writeGif(originalGifData, outputStream)
        }

        val readGifData = FileInputStream(tempFile).use { inputStream ->
            readGif(inputStream)
        }

        assertEquals(originalGifData.frames.size, readGifData.frames.size, "Number of frames should match")
        assertEquals(originalGifData.loopMode.loopCount, readGifData.loopMode.loopCount, "Loop mode type should match")

        for (i in originalGifData.frames.indices) {
            val originalFrame = originalGifData.frames[i]
            val readFrame = readGifData.frames[i]

            // Allow for some rounding in the duration (GIF stores time in 1/100th of a second)
            assertTrue(
                abs(originalFrame.durationInMs - readFrame.durationInMs) <= 10,
                "Frame $i duration should be approximately ${originalFrame.durationInMs} ms, but was ${readFrame.durationInMs} ms"
            )
            assertEquals(originalFrame.drawOffset, readFrame.drawOffset)
            assertEquals(originalFrame.image.size, readFrame.image.size)
        }
    }

    private fun createColorImage(size: Dimension, color: String): BufferedImage {
        val image = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()
        g2d.color = Color.decode(color)
        g2d.fillRect(0, 0, size.width, size.height)
        g2d.dispose()
        return image
    }
}
