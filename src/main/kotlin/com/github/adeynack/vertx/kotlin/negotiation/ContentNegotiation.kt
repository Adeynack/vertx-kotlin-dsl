package com.github.adeynack.vertx.kotlin.negotiation

data class NegotiationResult<out T>(
    val contentType: MimeDetail,
    val body: T
)

interface ContentNegotiator {

    val accepts: MimeDetail

    val produces: MimeDetail

    /**
     * Tries to perform a serialization from any object to the desired output.
     *
     * @param o the object to serialize.
     * @param acceptedContentType optional MIME type, to help the [ContentNegotiator] to serialize more
     *                            specifically to the needs of the caller.
     *
     * @return `null` if the negotiation was refused by the negotiator (ex: required MIME type
     *         if not supported); otherwise, a [NegotiationResult] containing the bytes of the
     *         serialized view of the object and its final serialized MIME type.
     */
    fun serialize(o: Any?, acceptedContentType: MimeDetail?): NegotiationResult<ByteArray>?

    /**
     * Tries to perform a deserialization of a raw content to the desired type [T].
     *
     * @param b the bytes to deserialize.
     * @param contentType optional MIME type, to help the [ContentNegotiator] to deserialize more
     *                    specifically to the needs of the caller.
     *
     * @return `null` if the negotiation was refused by the negotiator (ex: required MIME type
     *         if not supported); otherwise, a [NegotiationResult] containing the deserialized
     *         object of type [T] and its final deserialized MIME type.
     */
    fun <T> deserialize(b: ByteArray, clazz: Class<T>, contentType: MimeDetail?): NegotiationResult<T>?

}

inline fun <reified T> ContentNegotiator.deserialize(b: ByteArray, contentType: MimeDetail?) = deserialize(b, T::class.java, contentType)
