package app.podwave.rss

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.Reader

/** One podcast in an OPML subscription list. */
data class OpmlOutline(val title: String, val feedUrl: String)

/**
 * OPML 1.0/2.0 import and export, the de-facto format for moving podcast
 * subscriptions between apps.
 */
class Opml(private val newParser: () -> XmlPullParser) {

    /** Collects every outline with an `xmlUrl`, at any nesting depth (folders are flattened). */
    fun parse(reader: Reader): List<OpmlOutline> {
        val parser = newParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(reader)
        }
        val result = mutableListOf<OpmlOutline>()
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || !parser.name.equals("outline", ignoreCase = true)) {
                    continue
                }
                val url = parser.attribute("xmlUrl")?.trim()
                if (url.isNullOrEmpty()) continue
                val title = parser.attribute("text") ?: parser.attribute("title") ?: url
                result += OpmlOutline(title.trim(), url)
            }
        } catch (e: XmlPullParserException) {
            throw FeedParseException("Malformed OPML: ${e.message}", e)
        }
        return result.distinctBy { it.feedUrl }
    }

    companion object {
        fun export(outlines: List<OpmlOutline>, title: String = "Podwave subscriptions"): String = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<opml version="2.0">""")
            appendLine("  <head><title>${title.escapeXml()}</title></head>")
            appendLine("  <body>")
            for (outline in outlines) {
                val text = outline.title.escapeXml()
                appendLine("""    <outline type="rss" text="$text" title="$text" xmlUrl="${outline.feedUrl.escapeXml()}" />""")
            }
            appendLine("  </body>")
            appendLine("</opml>")
        }
    }
}

private fun XmlPullParser.attribute(name: String): String? {
    for (i in 0 until attributeCount) {
        if (getAttributeName(i).equals(name, ignoreCase = true)) return getAttributeValue(i)
    }
    return null
}

internal fun String.escapeXml(): String = buildString(length) {
    for (c in this@escapeXml) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}
