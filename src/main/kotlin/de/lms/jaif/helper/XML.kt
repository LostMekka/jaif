package de.lms.jaif.helper

import org.w3c.dom.Node
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.OutputKeys
import javax.xml.transform.stream.StreamResult
import java.io.StringWriter
import javax.imageio.metadata.IIOMetadataNode

internal fun Node.toPrettyXmlString(): String {
    val transformer = TransformerFactory
        .newInstance()
        .newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty("{https://xml.apache.org/xslt}indent-amount", "2")
        }
    return StringWriter().use { writer ->
        val source = DOMSource(this)
        val target = StreamResult(writer)
        transformer.transform(source, target)
        writer.toString()
    }
}

internal fun IIOMetadataNode.modifyChildNode(name: String, config: (IIOMetadataNode.() -> Unit)? = null): IIOMetadataNode {
    for (i in 0 until childNodes.length) {
        val node = childNodes.item(i)
        if (node.nodeName.equals(name, ignoreCase = true)) {
            node as IIOMetadataNode
            config?.invoke(node)
            return node
        }
    }
    return IIOMetadataNode(name).also {
        appendChild(it)
        config?.invoke(it)
    }
}

internal fun IIOMetadataNode.getChildNodeOrNull(name: String): IIOMetadataNode? {
    for (i in 0 until length) {
        val item = item(i) as IIOMetadataNode
        if (item.nodeName == name) return item
    }
    return null
}

internal fun IIOMetadataNode.attribute(name: String, value: Any) {
    setAttribute(name, value.toString())
}
