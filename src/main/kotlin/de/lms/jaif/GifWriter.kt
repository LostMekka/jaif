package de.lms.jaif

import java.awt.image.BufferedImage
import java.io.OutputStream
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.MemoryCacheImageOutputStream

internal class GifWriter(outputStream: OutputStream) : AutoCloseable {
    val imageWriter: ImageWriter
    val memoryCacheImageOutputStream: MemoryCacheImageOutputStream
    val imageWriteParam: ImageWriteParam
    val imageTypeSpecifier: ImageTypeSpecifier

    init {
        val imageTypeWriters = ImageIO.getImageWritersByFormatName("gif")
        if (!imageTypeWriters.hasNext()) throw IllegalStateException("No GIF image writers found")
        imageWriter = imageTypeWriters.next()
        memoryCacheImageOutputStream = MemoryCacheImageOutputStream(outputStream)
        imageWriter.output = memoryCacheImageOutputStream
        imageWriteParam = imageWriter.defaultWriteParam
        imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB)
    }

    fun createStreamMetadata(mutator: IIOMetadataNode.() -> Unit): IIOMetadata {
        val frameMetadata = imageWriter.getDefaultStreamMetadata(imageWriteParam)
        val frameMetadataTree = frameMetadata.getAsTree(frameMetadata.nativeMetadataFormatName) as IIOMetadataNode
        frameMetadataTree.mutator()
        frameMetadata.setFromTree(frameMetadata.nativeMetadataFormatName, frameMetadataTree)
        return frameMetadata
    }

    fun createFrameMetadata(mutator: IIOMetadataNode.() -> Unit): IIOMetadata {
        val frameMetadata = imageWriter.getDefaultImageMetadata(imageTypeSpecifier, imageWriteParam)
        val frameMetadataTree = frameMetadata.getAsTree(frameMetadata.nativeMetadataFormatName) as IIOMetadataNode
        frameMetadataTree.mutator()
        frameMetadata.setFromTree(frameMetadata.nativeMetadataFormatName, frameMetadataTree)
        return frameMetadata
    }

    override fun close() {
        memoryCacheImageOutputStream.close()
        imageWriter.dispose()
    }
}
