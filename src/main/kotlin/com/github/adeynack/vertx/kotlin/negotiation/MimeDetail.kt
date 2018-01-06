package com.github.adeynack.vertx.kotlin.negotiation

import java.nio.charset.Charset

data class MimeDetail(
    val primaryType: String,
    val subType: String,
    val charset: Charset? = null,
    val parameters: Map<String, String> = emptyMap()
) {

    companion object {

        fun parseOrNull(mimeType: String?): MimeDetail? = try {
            if (mimeType == null) null
            else parse(mimeType)
        } catch (e: Throwable) {
            null
        }

        /**
         * @throws MimeDetailParsingException when the [mimeType] string cannot parse to a [MimeDetail].
         * @throws java.nio.charset.UnsupportedCharsetException when the specified charset is not recognised.
         */
        fun parse(mimeType: String): MimeDetail {
            val parts = mimeType.split(";")

            val mimeTypeParts = parts.firstOrNull()?.split("/") ?: throw MimeDetailParsingException("MIME type cannot be empty.")
            if (mimeTypeParts.size != 2) {
                throw MimeDetailParsingException("""MIME type is expected to have 2 parts, separated by a "/". Example: `application/json` or `*/json`""")
            }
            val primaryType = mimeTypeParts[0]
            val subType = mimeTypeParts[1]

            val parameters = parts.drop(1)
                .map {
                    val p = it.split("=")
                    if (p.size != 2) {
                        throw MimeDetailParsingException("""Malformed MIME parameter: $it""")
                    }
                    p[0].trim().toLowerCase() to p[1]
                }
                .toMap()

            val charset = parameters[charsetParamName]?.let(Charset::forName)

            return MimeDetail(primaryType, subType, charset, parameters - charsetParamName)
        }

        private val wildcard = "*"
        private val charsetParamName = "charset"

        /**
         * Copied from [javax.activation.MimeType.isTokenChar]
         */
        private fun isTokenChar(c: Char): Boolean = c > ' ' && c.toInt() < 127 && """()<>@,;:/[]?=\"""".indexOf(c) < 0

        private fun validate(s: String) {
            if (!s.all(this::isTokenChar)) throw IllegalArgumentException("""Invalid value "$s". Values of a MIME type cannot contain tokens.""")
        }

    }

    init {
        validate(this.primaryType)
        validate(this.subType)
        parameters.forEach { k, v ->
            validate(k)
            validate(v)
        }
    }

    fun supports(other: MimeDetail): Boolean =
        (this.primaryType == wildcard || this.primaryType.equals(other.primaryType, true)) &&
            (this.subType == wildcard || this.subType.equals(other.subType, true)) &&
            (this.charset == null || this.charset == other.charset)

    override fun toString(): String {
        val sb = StringBuilder("$primaryType/$subType")
        fun appendParam(key: String, value: String) {
            sb.append("; ").append(key).append("=").append(value)
        }
        if (charset != null) {
            appendParam(charsetParamName, charset.name().toLowerCase())
        }
        parameters.forEach(::appendParam)
        return sb.toString()
    }
}

class MimeDetailParsingException(msg: String) : IllegalArgumentException(msg)
