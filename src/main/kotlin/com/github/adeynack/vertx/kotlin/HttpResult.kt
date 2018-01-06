package com.github.adeynack.vertx.kotlin

sealed class HttpResult(
    val statusCode: Int,
    val body: Any?
)

class Ok(body: Any? = null) : HttpResult(200, body)
class Created(body: Any? = null) : HttpResult(201, body)

class BadRequest(body: Any? = null) : HttpResult(400, body)
class Unauthorized(body: Any? = null) : HttpResult(401, body)
class Forbidden(body: Any? = null) : HttpResult(403, body)
class NotFound(body: Any? = null) : HttpResult(404, body)

class InternalServerError(body: Any? = null) : HttpResult(500, body)
