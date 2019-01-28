package com.github.adeynack.vertx.kotlin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod.GET
import io.vertx.core.http.HttpMethod.POST
import io.vertx.core.http.HttpMethod.PUT
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.LoggerHandler
import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(VertxUnitRunner::class)
class VertxRouteTest : VertxClientTest {

    override lateinit var vertx: Vertx

    private data class AccountIn(
        val name: String,
        val parentId: Int,
        val description: String
    )

    @Serializable
    private data class AccountOut(
        @SerialId(1) val id: Int,
        @SerialId(2) val name: String,
        @SerialId(3) val parentId: Int,
        @SerialId(4) val description: String
    )

    private val kotlinModule = KotlinModule()
    private val xmlMapper = XmlMapper().apply {
        registerModule(kotlinModule)
        propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
    }
    private val jsonMapper = ObjectMapper().apply {
        registerModule(kotlinModule)
        propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
    }

    private fun postAccount(accountToCreate: AccountIn) = AccountOut(
        id = 1234,
        name = accountToCreate.name,
        parentId = accountToCreate.parentId,
        description = accountToCreate.description
    )

    private inline fun <reified T: Any> readJson(ctx: RoutingContext): T {
        return jsonMapper.readValue(ctx.bodyAsString)
    }

    private inline fun <reified T: Any> readProtobuf(ctx: RoutingContext): T {
        return ProtoBuf.load(ctx.body.bytes)
    }

    val a: (RoutingContext) -> T = this::readProtobuf

    private val readers = mapOf(
        "*/json" to readJson,
        "*/x-protobuf" to readProtobuf
    )

    private inline fun <reified TOutput> Route.negotiate(crossinline handler: (RoutingContext) -> TOutput) {
        this.handler { ctx ->
            val responseBody = handler(ctx)
            ctx.response().end(responseBody.toString())
        }
    }

    private inline fun <reified TInput, reified TOutput> Route.negotiate(crossinline handler: (RoutingContext, TInput) -> TOutput) {
        this.handler { ctx ->
            val requestBody = null as TInput
            val responseBody = handler(ctx, requestBody)
            ctx.response().end(responseBody.toString())
        }
    }

    private fun getBookById(ctx: RoutingContext): String {
        return "hello"
    }

    private fun postBook(ctx: RoutingContext, accountIn: AccountIn): AccountOut {
        return AccountOut(1, "", 2, "")
    }

    private fun putBook(ctx: RoutingContext, accountIn: AccountIn): AccountOut {
        return AccountOut(1, "", 2, "")
    }

    @Before
    fun before(context: TestContext) {
        vertx = Vertx.vertx()
        Router.router(vertx).apply {
            route().handler(LoggerHandler.create())
            route().handler(BodyHandler.create())

            route(POST, "/api/books").negotiate(::postBook)
            route(GET, "/api/books/{bookId}").negotiate(::getBookById)
            route(PUT, "/api/books/{bookId}").negotiate(::putBook)

            route(POST, "/accounts")
                .consumes("*/json").produces("*/json")
                .handler { ctx ->
                    val i = jsonMapper.readValue<AccountIn>(ctx.body.bytes)
                    val o = postAccount(i)
                    ctx.response()
                        .putHeader("Content-Type", ctx.request().getHeader("accept"))
                        .end(jsonMapper.writeValueAsString(o))
                }
            route(POST, "/accounts")
                .consumes("*/json").produces("*/xml")
                .handler { ctx ->
                    val i = jsonMapper.readValue<AccountIn>(ctx.body.bytes)
                    val o = postAccount(i)
                    ctx.response()
                        .putHeader("Content-Type", ctx.request().getHeader("accept"))
                        .end(xmlMapper.writeValueAsString(o))
                }
            route(POST, "/accounts")
                .consumes("*/json")
                .produces("application/x-protobuf")
                .produces("application/protobuf")
                .handler { ctx ->
                    val i = jsonMapper.readValue<AccountIn>(ctx.body.bytes)
                    val o = postAccount(i)
                    ctx.response()
                        .putHeader("Content-Type", ctx.request().getHeader("accept"))
                        .end(Buffer.buffer(ProtoBuf.dump(o)))
                }
            route(POST, "/accounts")
                .consumes("*/xml").produces("*/json")
                .handler { ctx ->
                    val i = xmlMapper.readValue<AccountIn>(ctx.body.bytes)
                    val o = postAccount(i)
                    ctx.response()
                        .putHeader("Content-Type", ctx.request().getHeader("accept"))
                        .end(jsonMapper.writeValueAsString(o))
                }
            route(POST, "/accounts")
                .consumes("*/xml").produces("*/xml")
                .handler { ctx ->
                    val i = xmlMapper.readValue<AccountIn>(ctx.body.bytes)
                    val o = postAccount(i)
                    ctx.response()
                        .putHeader("Content-Type", ctx.request().getHeader("accept"))
                        .end(xmlMapper.writeValueAsString(o))
                }
            vertx.createHttpServer()
                .requestHandler(::accept)
                .listen(8080, context.asyncAssertSuccess())
        }
    }

    @After
    fun after(context: TestContext) {
        vertx.close(context.asyncAssertSuccess())
    }

    @Test
    fun `json in json out`(context: TestContext) =
        context.testClientCallBody({
            post(8080, "localhost", "/accounts", it)
                .putHeader("accept", "application/json")
                .putHeader("content-type", "application/json")
                .end("""
                    {
                        "name": "Foo Bar",
                        "parent_id": 9876,
                        "description": "Lorem Ipsum la la la"
                    }
                """)
        }) { response, body ->
            context.assertEquals(200, response.statusCode())
            context.assertEquals("application/json", response.getHeader("content-type"))
            context.assertEquals(
                jsonMapper.readTree("""
                    {
                        "id": 1234,
                        "name": "Foo Bar",
                        "parent_id": 9876,
                        "description": "Lorem Ipsum la la la"
                    }
                    """),
                jsonMapper.readTree(body.bytes))
        }

    @Test
    fun `json in json out (text)`(context: TestContext) =
        context.testClientCallBody({
            post(8080, "localhost", "/accounts", it)
                .putHeader("accept", "text/json")
                .putHeader("content-type", "application/json")
                .end("""
                    {
                        "name": "Foo Bar",
                        "parent_id": 9876,
                        "description": "Lorem Ipsum la la la"
                    }
                """)
        }) { response, body ->
            context.assertEquals(200, response.statusCode())
            context.assertEquals("text/json", response.getHeader("content-type"))
            println("Body was ${body.bytes.size} bytes")
            context.assertEquals(
                jsonMapper.readTree("""
                    {
                        "id": 1234,
                        "name": "Foo Bar",
                        "parent_id": 9876,
                        "description": "Lorem Ipsum la la la"
                    }
                    """),
                jsonMapper.readTree(body.bytes))
        }

    @Test
    fun `json in xml out`(context: TestContext) =
        context.testClientCallBody({
            post(8080, "localhost", "/accounts", it)
                .putHeader("accept", "text/xml")
                .putHeader("content-type", "application/json")
                .end("""
                    {
                        "name": "Foo Bar",
                        "parent_id": 9876,
                        "description": "Lorem Ipsum la la la"
                    }
                """)
        }) { response, body ->
            context.assertEquals(200, response.statusCode())
            context.assertEquals("text/xml", response.getHeader("content-type"))
            println("Body was ${body.bytes.size} bytes")
            context.assertEquals(
                xmlMapper.readTree("""
                    <account>
                        <id>1234</id>
                        <name>Foo Bar</name>
                        <parent_id>9876</parent_id>
                        <description>Lorem Ipsum la la la</description>
                    </account>
                    """),
                xmlMapper.readTree(body.bytes))
        }

    @Test
    fun `json in protobuf out`(context: TestContext) =
        context.testClientCallBody({
            post(8080, "localhost", "/accounts", it)
                .putHeader("accept", "application/protobuf")
                .putHeader("content-type", "application/json")
                .end("""
                    {
                        "name": "Foo Bar",
                        "parent_id": 9876,
                        "description": "Lorem Ipsum la la la"
                    }
                """)
        }) { response, body ->
            context.assertEquals(200, response.statusCode())
            context.assertEquals("application/protobuf", response.getHeader("content-type"))
            val received = ProtoBuf.load<AccountOut>(body.bytes)
            println("Body was ${body.bytes.size} bytes")
            context.assertEquals(received, AccountOut(
                id = 1234,
                name = "Foo Bar",
                parentId = 9876,
                description = "Lorem Ipsum la la la"))
        }

}
