package com.github.adeynack.vertx.kotlin

import com.github.adeynack.vertx.kotlin.negotiation.ContentNegotiator
import com.github.adeynack.vertx.kotlin.negotiation.MimeDetail
import com.github.adeynack.vertx.kotlin.negotiation.deserialize
import io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import java.nio.charset.Charset
import java.security.InvalidParameterException
import java.util.concurrent.CompletionStage

fun Router.negotiate(vararg contentNegotiators: ContentNegotiator, f: RouteWithContentNegotiation.() -> Unit) =
    f(RouteWithContentNegotiation(this, contentNegotiators.toList()))

@Suppress("MemberVisibilityCanPrivate") // fields are being used by inline/reified methods
data class RouteWithContentNegotiation(
    val router: Router,
    val negotiators: List<ContentNegotiator>,
    val pathPrefix: List<String> = emptyList()
) {

    init {
        if (negotiators.isEmpty()) {
            throw InvalidParameterException("At least one content negotiator needs to be provided.")
        }
    }

    companion object {
        object Headers {
            val contentType = "Content-Type"
            val accept = "Accept"
        }
    }

    inline fun <reified T> RoutingContext.withBody(crossinline f: (T) -> Unit) {
        try {
            val rawMime = request().getHeader(Headers.contentType)
            val mime = MimeDetail.parseOrNull(rawMime)
            val negotiator =
                if (mime == null) negotiators.first()
                else negotiators.firstOrNull { it.accepts.supports(mime) }
            if (negotiator == null) response().setStatusCode(BAD_REQUEST.code()).end()
            else {
                val rawBody = body.bytes
                if (rawBody.isEmpty()) response().setStatusCode(BAD_REQUEST.code()).end()
                else {
                    try {
                        val body = negotiator.deserialize<T>(rawBody, mime)
                        if (body == null) response().setStatusCode(BAD_REQUEST.code()).end()
                        else f(body.body)
                    } catch (t: Throwable) {
                        response().setStatusCode(BAD_REQUEST.code()).end()
                    }
                }
            }
        } catch (e: Throwable) {
            fail(e)
        }
    }

    fun Route.routeProducesAllNegotiators(): Route = negotiators.fold(this) { route, contentNegotiator ->
        route.produces(contentNegotiator.produces.toString())
    }

    fun Route.routeAcceptsAllNegotiators(): Route = negotiators.fold(this) { route, contentNegotiator ->
        route.consumes(contentNegotiator.accepts.toString())
    }.consumes("*/*")

    fun fullPath(): String =
        pathPrefix
            .filter { it.isNotBlank() }
            .joinToString("/", "/")

    fun autoRoute(
        httpMethod: HttpMethod = HttpMethod.GET,
        handler: (RoutingContext) -> CompletionStage<HttpResult>
    ) {
        val fullPath = fullPath()
        router
            .route(httpMethod, fullPath)
            .routeProducesAllNegotiators()
            .handler { ctx ->
                handler(ctx)
                    .thenAccept { ctx.respond(it) }
                    .exceptionally { t -> ctx.fail(t); null }
            }
        router.route(httpMethod, fullPath).handler { it.response().setStatusCode(400).end() }
    }

    inline fun <reified TReqBody> autoRouteWithBody(
        httpMethod: HttpMethod = HttpMethod.GET,
        crossinline handler: (RoutingContext, TReqBody) -> CompletionStage<HttpResult>
    ) {
        val fullPath = fullPath()
        router
            .route(httpMethod, fullPath)
            .routeAcceptsAllNegotiators()
            .routeProducesAllNegotiators()
            .handler { ctx ->
                ctx.withBody<TReqBody> { requestBody ->
                    handler(ctx, requestBody)
                        .thenAccept { ctx.respond(it) }
                        .exceptionally { t -> ctx.fail(t); null }
                }
            }
        router.route(httpMethod, fullPath).handler { it.response().setStatusCode(400).end() }
    }

    @Suppress("MemberVisibilityCanPrivate") // cannot be private because its used from public inline/reified functions
    fun RoutingContext.respond(response: HttpResult) {
        try {
            val (accept, nego) =
                if (acceptableContentType == null) {
                    val firstNego = negotiators.first()
                    firstNego.produces to firstNego
                } else {
                    val accept = MimeDetail.parse(acceptableContentType)
                    val nego = negotiators.firstOrNull { it.produces.supports(accept) }
                    val acceptHeaders = parsedHeaders().accept()
                    val acceptHeaderMime = acceptHeaders
                        .asSequence()
                        .map { MimeDetail(it.component() ?: "*", it.subComponent() ?: "*", it.parameter("charset")?.let(Charset::forName)) }
                        .firstOrNull { accept.supports(it) }
                        ?: nego?.produces
                    acceptHeaderMime to nego
                }

            val responseBody = nego?.serialize(response.body, accept)
            if (responseBody != null) {
                response()
                    .putHeader(Headers.contentType, responseBody.contentType.toString())
                    .setStatusCode(response.statusCode)
                    .end(Buffer.buffer(responseBody.body))
            } else {
                response().setStatusCode(500).end()
            }
        } catch (e: Throwable) {
            fail(e)
        }
    }

    //
    // Path building
    //

    infix operator fun String.invoke(f: RouteWithContentNegotiation.() -> Unit) {
        val parts = this.split("/")
        val newPathPrefix = pathPrefix + parts
        f(this@RouteWithContentNegotiation.copy(
            pathPrefix = newPathPrefix
        ))
    }

    //
    // GET
    //

    @Suppress("FunctionName")
    fun GET(handler: () -> CompletionStage<HttpResult>) {
        autoRoute(HttpMethod.GET, { handler() })
    }

    @Suppress("FunctionName")
    fun GET(handler: (RoutingContext) -> CompletionStage<HttpResult>) {
        autoRoute(HttpMethod.GET, handler)
    }

    //
    // POST
    //

    @Suppress("FunctionName")
    inline fun <reified TReqBody> POST(crossinline handler: (TReqBody) -> CompletionStage<HttpResult>) {
        autoRouteWithBody(HttpMethod.POST, { _, body: TReqBody -> handler(body) })
    }

    @Suppress("FunctionName")
    inline fun <reified TReqBody> POST(crossinline handler: (RoutingContext, TReqBody) -> CompletionStage<HttpResult>) {
        autoRouteWithBody(HttpMethod.POST, handler)
    }

    @Suppress("FunctionName")
    inline fun POST_blank(crossinline handler: () -> CompletionStage<HttpResult>) {
        autoRoute(HttpMethod.POST, { _ -> handler() })
    }

    @Suppress("FunctionName")
    fun POST_blank(handler: (RoutingContext) -> CompletionStage<HttpResult>) {
        autoRoute(HttpMethod.POST, handler)
    }

}
