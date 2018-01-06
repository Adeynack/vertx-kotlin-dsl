package com.github.adeynack.vertx.kotlin.negotiation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.UnsupportedCharsetException

class MimeDetailTest {

    //
    // construction
    //

    @Test
    fun `construct -- works for valid parameters`() {
        val m = MimeDetail("application", "json", Charsets.UTF_8)
        assertEquals("application", m.primaryType)
        assertEquals("json", m.subType)
        assertEquals(Charsets.UTF_8, m.charset)
        assertTrue(m.parameters.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `construct -- fails if primary type contains a non-token character`() {
        MimeDetail("somethingWithInvalidCharacter(", "json", Charsets.UTF_8)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `construct -- fails if sub type contains a non-token character`() {
        MimeDetail("application", "json(", Charsets.UTF_8)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `construct -- fails if one of the parameter contains a non-token character in a key`() {
        MimeDetail("application", "json", Charsets.UTF_8, mapOf(
            "a" to "x",
            "b(" to "y",
            "c" to "z"
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `construct -- fails if one of the parameter contains a non-token character in a value`() {
        MimeDetail("application", "json", Charsets.UTF_8, mapOf(
            "a" to "x",
            "b" to "y",
            "c" to "z("
        ))
    }

    //
    // parse
    //

    @Test
    fun `parse -- works for typical JSON type with UTF-8 charset (specified in uppercase)`() {
        val m = MimeDetail.parse("application/json; charset=UTF-8")
        val expected = MimeDetail("application", "json", Charsets.UTF_8)
        assertEquals(expected, m)
    }

    @Test
    fun `parse -- works for typical JSON type with UTF-8 charset (specified in mixed case)`() {
        val m = MimeDetail.parse("application/json; charset=Utf-8")
        val expected = MimeDetail("application", "json", Charsets.UTF_8)
        assertEquals(expected, m)
    }

    @Test
    fun `parse -- works for wildcard-JSON type`() {
        val m = MimeDetail.parse("*/json")
        val expected = MimeDetail("*", "json")
        assertEquals(expected, m)
    }

    @Test
    fun `parse -- works for typical JSON type with ASCII charset (specified in lower case)`() {
        val m = MimeDetail.parse("application/json; charset=ascii")
        val expected = MimeDetail("application", "json", Charsets.US_ASCII)
        assertEquals(expected, m)
    }

    @Test(expected = UnsupportedCharsetException::class)
    fun `parse -- throws when the charset is unknown`() {
        MimeDetail.parse("application/json; charset=foo")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse -- throws when the charset is incomplete`() {
        MimeDetail.parse("application/json; charset")
    }

    @Test
    fun `parse -- works for typical JSON type with UTF-8 charset and parameters`() {
        val m = MimeDetail.parse("application/json; charset=Utf-8; foo=bar; bleh=bleu")
        val expected = MimeDetail("application", "json", Charsets.UTF_8, mapOf("foo" to "bar", "bleh" to "bleu"))
        assertEquals(expected, m)
    }

    @Test
    fun `parse -- works for typical JSON type without charset and parameters`() {
        val m = MimeDetail.parse("application/json; foo=bar; bleh=bleu")
        val expected = MimeDetail("application", "json", null, mapOf("foo" to "bar", "bleh" to "bleu"))
        assertEquals(expected, m)
    }

    //
    // supports
    //

    @Test
    fun `support -- *-*`() {
        val m = MimeDetail("*", "*", null)
        assertTrue(m.supports(m))
        assertTrue(m.supports(MimeDetail.parse("foo/*")))
        assertTrue(m.supports(MimeDetail.parse("*/bar")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar")))
        assertTrue(m.supports(MimeDetail.parse("foo/*; charset=utf-8")))
        assertTrue(m.supports(MimeDetail.parse("*/bar; charset=utf-8")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar; charset=utf-8")))
    }

    @Test
    fun `support -- *-* with charset`() {
        val m = MimeDetail("*", "*", Charsets.UTF_16BE)
        assertTrue(m.supports(m))

        assertFalse(m.supports(MimeDetail.parse("foo/*")))
        assertFalse(m.supports(MimeDetail.parse("*/bar")))
        assertFalse(m.supports(MimeDetail.parse("foo/bar")))

        assertFalse(m.supports(MimeDetail.parse("foo/*; charset=utf-8")))
        assertFalse(m.supports(MimeDetail.parse("*/bar; charset=utf-8")))
        assertFalse(m.supports(MimeDetail.parse("foo/bar; charset=utf-8")))

        assertTrue(m.supports(MimeDetail.parse("foo/*; charset=utf-16be")))
        assertTrue(m.supports(MimeDetail.parse("*/bar; charset=utf-16be")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar; charset=utf-16be")))
    }

    @Test
    fun `support -- foo-*`() {
        val m = MimeDetail("foo", "*", null)
        assertTrue(m.supports(m))

        assertTrue(m.supports(MimeDetail.parse("foo/*")))
        assertFalse(m.supports(MimeDetail.parse("*/bar")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar")))

        assertTrue(m.supports(MimeDetail.parse("foo/*; charset=utf-8")))
        assertFalse(m.supports(MimeDetail.parse("*/bar; charset=utf-8")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar; charset=utf-8")))

        assertTrue(m.supports(MimeDetail.parse("foo/*; charset=utf-16be")))
        assertFalse(m.supports(MimeDetail.parse("*/bar; charset=utf-16be")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar; charset=utf-16be")))
    }

    @Test
    fun `support -- foo-* with charset`() {
        val m = MimeDetail("foo", "*", Charsets.UTF_8)
        assertTrue(m.supports(m))

        assertFalse(m.supports(MimeDetail.parse("foo/*")))
        assertFalse(m.supports(MimeDetail.parse("*/bar")))
        assertFalse(m.supports(MimeDetail.parse("foo/bar")))

        assertTrue(m.supports(MimeDetail.parse("foo/*; charset=utf-8")))
        assertFalse(m.supports(MimeDetail.parse("*/bar; charset=utf-8")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar; charset=utf-8")))

        assertFalse(m.supports(MimeDetail.parse("foo/*; charset=utf-16be")))
        assertFalse(m.supports(MimeDetail.parse("*/bar; charset=utf-16be")))
        assertFalse(m.supports(MimeDetail.parse("foo/bar; charset=utf-16be")))
    }

    @Test
    fun `support -- *-bar`() {
        val m = MimeDetail("*", "bar", null)
        assertTrue(m.supports(m))

        assertFalse(m.supports(MimeDetail.parse("foo/*")))
        assertTrue(m.supports(MimeDetail.parse("*/bar")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar")))

        assertFalse(m.supports(MimeDetail.parse("foo/*; charset=utf-8")))
        assertTrue(m.supports(MimeDetail.parse("*/bar; charset=utf-8")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar; charset=utf-8")))

        assertFalse(m.supports(MimeDetail.parse("foo/*; charset=utf-16be")))
        assertTrue(m.supports(MimeDetail.parse("*/bar; charset=utf-16be")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar; charset=utf-16be")))
    }

    @Test
    fun `support -- *-bar with charset`() {
        val m = MimeDetail("*", "bar", Charsets.UTF_8)
        assertTrue(m.supports(m))

        assertFalse(m.supports(MimeDetail.parse("foo/*")))
        assertFalse(m.supports(MimeDetail.parse("*/bar")))
        assertFalse(m.supports(MimeDetail.parse("foo/bar")))

        assertFalse(m.supports(MimeDetail.parse("foo/*; charset=utf-8")))
        assertTrue(m.supports(MimeDetail.parse("*/bar; charset=utf-8")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar; charset=utf-8")))

        assertFalse(m.supports(MimeDetail.parse("foo/*; charset=utf-16be")))
        assertFalse(m.supports(MimeDetail.parse("*/bar; charset=utf-16be")))
        assertFalse(m.supports(MimeDetail.parse("foo/bar; charset=utf-16be")))
    }

    @Test
    fun `support -- foo-bar`() {
        val m = MimeDetail("foo", "bar", null)
        assertTrue(m.supports(m))

        assertFalse(m.supports(MimeDetail.parse("foo/*")))
        assertFalse(m.supports(MimeDetail.parse("*/bar")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar")))

        assertFalse(m.supports(MimeDetail.parse("foo/*; charset=utf-8")))
        assertFalse(m.supports(MimeDetail.parse("*/bar; charset=utf-8")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar; charset=utf-8")))

        assertFalse(m.supports(MimeDetail.parse("foo/*; charset=utf-16be")))
        assertFalse(m.supports(MimeDetail.parse("*/bar; charset=utf-16be")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar; charset=utf-16be")))
    }

    @Test
    fun `support -- foo-bar with charset`() {
        val m = MimeDetail("foo", "bar", Charsets.UTF_8)
        assertTrue(m.supports(m))

        assertFalse(m.supports(MimeDetail.parse("foo/*")))
        assertFalse(m.supports(MimeDetail.parse("*/bar")))
        assertFalse(m.supports(MimeDetail.parse("foo/bar")))

        assertFalse(m.supports(MimeDetail.parse("foo/*; charset=utf-8")))
        assertFalse(m.supports(MimeDetail.parse("*/bar; charset=utf-8")))
        assertTrue(m.supports(MimeDetail.parse("foo/bar; charset=utf-8")))

        assertFalse(m.supports(MimeDetail.parse("foo/*; charset=utf-16be")))
        assertFalse(m.supports(MimeDetail.parse("*/bar; charset=utf-16be")))
        assertFalse(m.supports(MimeDetail.parse("foo/bar; charset=utf-16be")))
    }

    //
    // toString
    //

    @Test
    fun `toString -- returns the expected value for typical JSON type with UTF-8 charset`() {
        val m = MimeDetail("application", "json", Charsets.UTF_8)
        assertEquals("application/json; charset=utf-8", m.toString())
    }

    @Test
    fun `toString -- returns the expected value for typical JSON type with UTF-8 charset and with parameter`() {
        val m = MimeDetail("application", "json", Charsets.UTF_8, mapOf("foo" to "bar", "bleh" to "bleu"))
        assertEquals("application/json; charset=utf-8; foo=bar; bleh=bleu", m.toString())
    }

    @Test
    fun `toString -- returns the expected value for typical JSON type without charset and with parameter`() {
        val m = MimeDetail("application", "json", null, mapOf("foo" to "bar", "bleh" to "bleu"))
        assertEquals("application/json; foo=bar; bleh=bleu", m.toString())
    }

    @Test
    fun `toString -- returns the expected value for typical JSON type without charset`() {
        val m = MimeDetail("application", "json")
        assertEquals("application/json", m.toString())
    }

}
