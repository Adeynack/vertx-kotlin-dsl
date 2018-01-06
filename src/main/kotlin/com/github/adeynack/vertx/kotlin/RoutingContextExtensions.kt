package com.github.adeynack.vertx.kotlin

import io.vertx.ext.web.RoutingContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

fun RoutingContext.pathParam(name: String, body: (String) -> CompletionStage<HttpResult>): CompletionStage<HttpResult> =
    pathParam(name, { it }, body)

fun <T> RoutingContext.pathParam(name: String, extractor: (String) -> T, body: (T) -> CompletionStage<HttpResult>): CompletionStage<HttpResult> {
    val parameterValue = try {
        val raw = pathParam(name)
        if (raw == null) {
            return CompletableFuture.completedFuture(NotFound())
        } else {
            extractor(raw)
        }
    } catch (e: Throwable) {
        return CompletableFuture.completedFuture(NotFound())
    }
    return body(parameterValue)
}
