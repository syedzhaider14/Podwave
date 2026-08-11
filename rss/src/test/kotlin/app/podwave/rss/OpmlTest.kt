package app.podwave.rss

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader

class OpmlTest {
    private val opml = Opml { KXmlParser() }

    @Test
    fun `parses nested outlines and ignores folders`() {
        val outlines = opml.parse(
            StringReader(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <opml version="1.0">
                  <head><title>Exported</title></head>
                  <body>
                    <outline text="Science">
                      <outline type="rss" text="Show A" xmlUrl="https://a.example/feed" />
                      <outline type="rss" title="Show B" xmlurl="https://b.example/feed" />
                    </outline>
                    <outline type="rss" text="Show A again" xmlUrl="https://a.example/feed" />
                    <outline type="rss" xmlUrl="https://c.example/feed" />
                  </body>
                </opml>
                """.trimIndent(),
            ),
        )
        assertThat(outlines).containsExactly(
            OpmlOutline("Show A", "https://a.example/feed"),
            OpmlOutline("Show B", "https://b.example/feed"),
            OpmlOutline("https://c.example/feed", "https://c.example/feed"),
        ).inOrder()
    }

    @Test
    fun `export round-trips, escaping special characters`() {
        val original = listOf(
            OpmlOutline("Tom & Jerry's \"Show\" <live>", "https://example.com/feed?a=1&b=2"),
            OpmlOutline("Plain", "https://plain.example/rss"),
        )
        val xml = Opml.export(original)

        assertThat(xml).contains("Tom &amp; Jerry&apos;s &quot;Show&quot; &lt;live&gt;")
        assertThat(opml.parse(StringReader(xml))).isEqualTo(original)
    }
}
