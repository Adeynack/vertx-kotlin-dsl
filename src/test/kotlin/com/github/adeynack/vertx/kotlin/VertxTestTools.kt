package com.github.adeynack.vertx.kotlin

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientResponse
import io.vertx.ext.unit.TestContext

interface VertxClientTest {

    val vertx: Vertx

    fun TestContext.testClientCall(
        clientCaller: HttpClient.(Handler<HttpClientResponse>) -> Unit,
        tester: (HttpClientResponse) -> Unit
    ) {
        val async = this.async()
        val client = vertx.createHttpClient()
        val handler = Handler<HttpClientResponse> { response ->
            try {
                tester(response)
            } catch (e: Throwable) {
                this.fail(e)
            } finally {
                client.close()
                async.complete()
            }
        }
        clientCaller(client, handler)
    }

    fun TestContext.testClientCallBody(
        clientCaller: HttpClient.(Handler<HttpClientResponse>) -> Unit,
        tester: (HttpClientResponse, Buffer) -> Unit
    ) {
        this.testClientCall(clientCaller) { response ->
            response.bodyHandler { body ->
                tester(response, body)
            }
        }
    }

}
