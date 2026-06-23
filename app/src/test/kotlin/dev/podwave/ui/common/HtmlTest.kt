package dev.podwave.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HtmlTest {

    @Test
    fun `paragraphs become blank-line separated text`() {
        assertThat(Html.toPlainText("<p>Hello&nbsp;<b>world</b></p><p>Second &amp; last</p>"))
            .isEqualTo("Hello world\n\nSecond & last")
    }

    @Test
    fun `line breaks and list items are kept`() {
        assertThat(Html.toPlainText("Line one<br/>Line two<BR>Line three")).isEqualTo("Line one\nLine two\nLine three")
        assertThat(Html.toPlainText("<ul><li>One</li><li class=\"x\">Two</li></ul>")).isEqualTo("• One\n• Two")
    }

    @Test
    fun `entities are decoded once`() {
        assertThat(Html.toPlainText("&lt;b&gt; is bold, &#169; 2024, &#x1F399;&#xFE0F;")).isEqualTo("<b> is bold, © 2024, 🎙️")
        assertThat(Html.toPlainText("Escaped: &amp;lt;tag&amp;gt;")).isEqualTo("Escaped: &lt;tag&gt;")
        assertThat(Html.toPlainText("Bogus &#99999999; stays")).isEqualTo("Bogus &#99999999; stays")
    }

    @Test
    fun `whitespace is tidied without losing paragraphs`() {
        assertThat(Html.toPlainText("  Lots   of\n\n\n\n   space  ")).isEqualTo("Lots of\n\nspace")
    }

    @Test
    fun `plain text is recognised as such`() {
        assertThat(Html.looksLikeHtml("Episode 3 < Episode 4, obviously")).isFalse()
        assertThat(Html.looksLikeHtml("<p>Notes</p>")).isTrue()
        assertThat(Html.looksLikeHtml("Line<br/>break")).isTrue()
    }
}
