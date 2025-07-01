package de.lms.jaif

import java.io.InputStream
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.ImageInputStream

internal class GifReader(inputStream: InputStream) : AutoCloseable {
    val imageReader: ImageReader = ImageIO.getImageReadersByFormatName("gif").next()
    val imageInputStream: ImageInputStream = ImageIO.createImageInputStream(inputStream)

    init {
        imageReader.input = imageInputStream
    }

    fun getStreamMetadata(): IIOMetadataNode {
        val metadata = imageReader.streamMetadata
        return metadata.getAsTree(metadata.nativeMetadataFormatName) as IIOMetadataNode
    }

    fun getFrameMetadata(frameIndex: Int): IIOMetadataNode {
        val metadata = imageReader.getImageMetadata(frameIndex)
        return metadata.getAsTree(metadata.nativeMetadataFormatName) as IIOMetadataNode
    }

    override fun close() {
        imageReader.dispose()
        imageInputStream.close()
    }
}
