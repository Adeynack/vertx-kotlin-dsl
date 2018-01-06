package com.github.adeynack.vertx.kotlin

import com.github.adeynack.vertx.kotlin.negotiation.ContentNegotiator
import com.github.adeynack.vertx.kotlin.negotiation.JsonNegotiator
import com.github.adeynack.vertx.kotlin.negotiation.MimeDetail
import com.github.adeynack.vertx.kotlin.negotiation.NegotiationResult
import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.LoggerHandler
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletionStage

@RunWith(VertxUnitRunner::class)
class RouterDslTest : VertxClientTest {

    data class Foo(
        val name: String,
        val extraInformation: String? = null
    )

    override lateinit var vertx: Vertx

    private data class FooByIdDetail(val id: Int, val childId: String)

    private val controller = object {

        fun getFoo(): CompletionStage<HttpResult> =
            completedFuture(
                Ok(listOf(
                    Foo("Foo#1"),
                    Foo("Foo#2", "Something more éèêàùß!"),
                    Foo("Foo#3")
                ))
            )

        fun getFooById(ctx: RoutingContext): CompletionStage<HttpResult> =
            ctx.pathParam("id", String::toInt) { id: Int ->
                ctx.pathParam("childId") { childId: String ->
                    completedFuture(
                        Ok(FooByIdDetail(id, childId))
                    )
                }
            }

        fun postFoo(foo: Foo): CompletionStage<HttpResult> =
            completedFuture(
                Created(listOf("Created `foo` with name ${foo.name}"))
            )

        fun postFooById(ctx: RoutingContext, foo: Foo): CompletionStage<HttpResult> =
            ctx.pathParam("id", String::toInt) { id ->
                completedFuture(
                    Created(listOf("Created `foo` with name ${foo.name} and ID $id"))
                )
            }

        fun postBlankFoo(): CompletionStage<HttpResult> =
            completedFuture(
                Created(Foo("Created `foo`", "Created from blank POST request"))
            )

        fun postBlankFooById(ctx: RoutingContext): CompletionStage<HttpResult> =
            ctx.pathParam("id", String::toInt) { id ->
                completedFuture(
                    Created(Foo("Created `foo` with ID $id", "Created from blank POST request"))
                )
            }

        fun getBarByIdBlehById(ctx: RoutingContext): CompletionStage<HttpResult> =
            completedFuture(Ok(
                arrayOf("""It worked, with `bar` ID "${ctx.pathParam("barId")}" and `bleh` ID "${ctx.pathParam("blehId")}"""")
            ))

    }

    private object HardCodedString : ContentNegotiator {

        override val accepts = MimeDetail("application", "HardCodedString")

        override val produces = MimeDetail("application", "HardCodedString")

        override fun serialize(o: Any?, acceptedContentType: MimeDetail?): NegotiationResult<ByteArray>? =
            NegotiationResult(acceptedContentType!!, "Hard Coded String".toByteArray())

        override fun <T> deserialize(b: ByteArray, clazz: Class<T>, contentType: MimeDetail?): NegotiationResult<T>? =
            throw NotImplementedError("Will not implement for tests")
    }

    @Before
    fun before(context: TestContext) {
        vertx = Vertx.vertx()
        Router.router(vertx).apply {
            route().handler(LoggerHandler.create())
            route().handler(BodyHandler.create())
            negotiate(JsonNegotiator(), HardCodedString) {
                "foo" {
                    GET(controller::getFoo)
                    POST(controller::postFoo)
                    "new" {
                        // important to have /foo/new BEFORE /foo/:id otherwise it is bypassed by it
                        POST_blank(controller::postBlankFoo)
                    }
                    ":id" {
                        "child/:childId" { GET(controller::getFooById) }
                        "new" { POST_blank(controller::postBlankFooById) }
                        POST(controller::postFooById)
                    }
                }
                // still possible to provide a full stand alone path
                "bar/:barId/bleh/:blehId" { GET(controller::getBarByIdBlehById) }
            }

            vertx.createHttpServer()
                .requestHandler(this::accept)
                .listen(8080, context.asyncAssertSuccess())
        }
    }

    @After
    fun after(context: TestContext) {
        vertx.close(context.asyncAssertSuccess())
    }

    //
    // `Accept` header tests + GET method tests
    //

    @Test
    fun `GET -foo -- no Accept header -- success -- uses the first negotiator`(context: TestContext) =
        context.testClientCallBody({
            getNow(8080, "localhost", "/foo", it)
        }) { response, body ->
            context.assertEquals(200, response.statusCode())
            context.assertEquals("application/json; charset=utf-8", response.getHeader("Content-Type"))
            context.assertEquals(
                json {
                    array(
                        obj("name" to "Foo#1"),
                        obj("name" to "Foo#2", "extra_information" to "Something more éèêàùß!"),
                        obj("name" to "Foo#3")
                    )
                }.toString(),
                body.toString())
        }

    @Test
    fun `GET -foo -- with Accept header JSON -- success -- uses the specific negotiator`(context: TestContext) =
        context.testClientCallBody({
            get(8080, "localhost", "/foo", it)
                .putHeader("Accept", "application/json")
                .end()
        }) { response, body ->
            context.assertEquals(200, response.statusCode())
            context.assertEquals("application/json; charset=utf-8", response.getHeader("Content-Type"))
            context.assertEquals(
                json {
                    array(
                        obj("name" to "Foo#1"),
                        obj("name" to "Foo#2", "extra_information" to "Something more éèêàùß!"),
                        obj("name" to "Foo#3")
                    )
                }.toString(),
                body.toString())
        }

    @Test
    fun `GET -foo -- with Accept header JSON in ISO-8859-1 -- success -- uses the specific negotiator`(context: TestContext) =
        context.testClientCallBody({
            get(8080, "localhost", "/foo", it)
                .putHeader("Accept", "application/json;charset=iso-8859-1")
                .end()
        }) { response, body ->
            context.assertEquals(200, response.statusCode())
            context.assertEquals("application/json; charset=iso-8859-1", response.getHeader("Content-Type"))
            context.assertEquals(
                json {
                    array(
                        obj("name" to "Foo#1"),
                        obj("name" to "Foo#2", "extra_information" to "Something more éèêàùß!"),
                        obj("name" to "Foo#3")
                    )
                }.toString(),
                body.toString(Charsets.ISO_8859_1))
        }

    @Test
    fun `GET -foo -- with multiple Accept header JSON,Text -- success -- uses the specific negotiator`(context: TestContext) =
        context.testClientCallBody({
            get(8080, "localhost", "/foo", it)
                .putHeader("Accept", "text/plain,application/json")
                .end()
        }) { response, body ->
            context.assertEquals(200, response.statusCode())
            context.assertEquals("application/json; charset=utf-8", response.getHeader("Content-Type"))
            context.assertEquals(
                json {
                    array(
                        obj("name" to "Foo#1"),
                        obj("name" to "Foo#2", "extra_information" to "Something more éèêàùß!"),
                        obj("name" to "Foo#3")
                    )
                }.toString(),
                body.toString())
        }

    @Test
    fun `GET -foo -- with Accept header as total wildcard -- success -- uses the first negotiator`(context: TestContext) =
        context.testClientCallBody({
            get(8080, "localhost", "/foo", it)
                .putHeader("Accept", "*/*")
                .end()
        }) { response, body ->
            context.assertEquals(200, response.statusCode())
            context.assertEquals("application/json; charset=utf-8", response.getHeader("Content-Type"))
            context.assertEquals(
                json {
                    array(
                        obj("name" to "Foo#1"),
                        obj("name" to "Foo#2", "extra_information" to "Something more éèêàùß!"),
                        obj("name" to "Foo#3")
                    )
                }.toString(),
                body.toString())
        }

    @Test
    fun `GET -foo -- with Accept header HardCodedString -- success -- uses the specific negotiator`(context: TestContext) =
        context.testClientCallBody({
            get(8080, "localhost", "/foo", it)
                .putHeader("Accept", "application/HardCodedString")
                .end()
        }) { response, body ->
            context.assertEquals(200, response.statusCode())
            context.assertEquals("application/HardCodedString", response.getHeader("Content-Type"))
            context.assertEquals("Hard Coded String", body.toString())
        }

    @Test
    fun `GET -foo -- with multiple Accept header JSON and HardCodedString, with qualifier higher on HardCodedString -- success -- uses the specific negotiator`(context: TestContext) =
        context.testClientCallBody({
            get(8080, "localhost", "/foo", it)
                .putHeader("Accept", "application/json;q=0.1,application/HardCodedString;q=0.2")
                .end()
        }) { response, body ->
            context.assertEquals(200, response.statusCode())
            context.assertEquals("application/HardCodedString", response.getHeader("Content-Type"))
            context.assertEquals("Hard Coded String", body.toString())
        }

    @Test
    fun `GET -foo -- with Accept header Nonsense -- fail with a 400 -- the specified Accept MIME type is not supported`(context: TestContext) =
        context.testClientCall({
            get(8080, "localhost", "/foo", it)
                .putHeader("Accept", "application/Nonsense")
                .end()
        }) { response ->
            context.assertEquals(400, response.statusCode())
        }

    //
    // Other methods tests
    //

    @Test
    fun `GET -foo-{id} -- success`(context: TestContext) =
        context.testClientCallBody({
            getNow(8080, "localhost", "/foo/123/child/asdf", it)
        }) { response, body ->
            context.assertEquals(200, response.statusCode())
            context.assertEquals("application/json; charset=utf-8", response.getHeader("Content-Type"))
            context.assertEquals(
                json { obj("id" to 123, "child_id" to "asdf") }.toString(),
                body.toString())
        }

    @Test
    fun `POST -foo -- empty body -- fails with BAD_REQUEST`(context: TestContext) =
        context.testClientCall({
            post(8080, "localhost", "/foo", it).end()
        }) { response ->
            context.assertEquals(400, response.statusCode())
        }

    @Test
    fun `POST -foo -- unable to parse body -- fails with BAD_REQUEST`(context: TestContext) =
        context.testClientCall({
            post(8080, "localhost", "/foo", it)
                .end(json { obj("something_that_does_not_exist_in_a_foo_object" to 42) }.toBuffer())
        }) { response ->
            context.assertEquals(400, response.statusCode())
        }

    @Test
    fun `POST -foo -- with Accept header Nonsense -- fail with a 400 -- the specified Accept MIME type is not supported`(context: TestContext) =
        context.testClientCall({
            post(8080, "localhost", "/foo", it)
                .putHeader("Accept", "application/Nonsense")
                .end()
        }) { response ->
            context.assertEquals(400, response.statusCode())
        }

    @Test
    fun `POST -foo -- with body -- results in CREATED`(context: TestContext) =
        context.testClientCallBody({
            post(8080, "localhost", "/foo", it)
                .end(json { obj("name" to "Test") }.toBuffer())
        }) { response, body ->
            context.assertEquals(201, response.statusCode())
            context.assertEquals("application/json; charset=utf-8", response.getHeader("Content-Type"))
            context.assertEquals(
                json { array("Created `foo` with name Test") }.toString(),
                body.toString())
        }

    @Test
    fun `POST -foo-{id} -- with body -- results in CREATED`(context: TestContext) =
        context.testClientCallBody({
            post(8080, "localhost", "/foo/123", it)
                .end(json { obj("name" to "Test") }.toBuffer())
        }) { response, body ->
            context.assertEquals(201, response.statusCode())
            context.assertEquals("application/json; charset=utf-8", response.getHeader("Content-Type"))
            context.assertEquals(
                json { array("Created `foo` with name Test and ID 123") }.toString(),
                body.toString())
        }

    @Test
    fun `POST -foo-new -- with empty body -- results in CREATED`(context: TestContext) =
        context.testClientCallBody({
            post(8080, "localhost", "/foo/new", it).end()
        }) { response, body ->
            context.assertEquals(201, response.statusCode())
            context.assertEquals("application/json; charset=utf-8", response.getHeader("Content-Type"))
            context.assertEquals(
                json { obj("name" to "Created `foo`", "extra_information" to "Created from blank POST request") }.toString(),
                body.toString())
        }

    @Test
    fun `POST -foo-{id}-new -- with empty body -- results in CREATED`(context: TestContext) =
        context.testClientCallBody({
            post(8080, "localhost", "/foo/123/new", it).end()
        }) { response, body ->
            context.assertEquals(201, response.statusCode())
            context.assertEquals("application/json; charset=utf-8", response.getHeader("Content-Type"))
            context.assertEquals(
                json { obj("name" to "Created `foo` with ID 123", "extra_information" to "Created from blank POST request") }.toString(),
                body.toString())
        }

    @Test
    fun `GET -bar-{id}-bleh-{blehId} -- success`(context: TestContext) =
        context.testClientCallBody({
            get(8080, "localhost", "/bar/asd/bleh/42", it).end()
        }) { response, body ->
            context.assertEquals(200, response.statusCode())
            context.assertEquals("application/json; charset=utf-8", response.getHeader("Content-Type"))
            context.assertEquals(
                json { array("""It worked, with `bar` ID "asd" and `bleh` ID "42"""") },
                body.toJsonArray())
        }

}
