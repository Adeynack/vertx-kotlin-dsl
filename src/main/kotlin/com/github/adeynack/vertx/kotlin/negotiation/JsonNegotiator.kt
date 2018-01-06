package com.github.adeynack.vertx.kotlin.negotiation

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.KotlinModule

class JsonNegotiator(
    private val mapper: ObjectMapper = createDefaultObjectMapper()
) : ContentNegotiator {

    companion object {

        private fun createDefaultObjectMapper() = ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .registerModule(KotlinModule())

        private val defaultCharset = Charsets.UTF_8

        private val defaultContentType = MimeDetail("*", "json", defaultCharset)

    }

    override val accepts = MimeDetail("*", "json")

    override val produces = MimeDetail("application", "json")

    override fun serialize(o: Any?, acceptedContentType: MimeDetail?): NegotiationResult<ByteArray>? {
        val producedContentType: MimeDetail = acceptedContentType?.let {
            if (it.charset == null) it.copy(charset = defaultCharset) else it
        } ?: defaultContentType
        return NegotiationResult(
            producedContentType,
            mapper.writeValueAsString(o).toByteArray(producedContentType.charset!!))
    }

    override fun <T> deserialize(b: ByteArray, clazz: Class<T>, contentType: MimeDetail?): NegotiationResult<T>? {
        if (contentType?.charset == null) {
            // let the `ObjectMapper` determine the encoding itself.
            return NegotiationResult(
                defaultContentType.copy(charset = null),
                mapper.readValue(b, clazz))
        }
        val contentInCharset = String(b, contentType.charset)
        return NegotiationResult(contentType, mapper.readValue(contentInCharset, clazz))
    }

}
