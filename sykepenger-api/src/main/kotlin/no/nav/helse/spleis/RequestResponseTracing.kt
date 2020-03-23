package no.nav.helse.spleis

import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.request.httpMethod
import io.ktor.request.uri
import io.ktor.response.ApplicationSendPipeline
import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import org.slf4j.Logger

internal fun Application.requestResponseTracing(logger: Logger) {
    val httpRequestCounter = Counter.build(
        "http_requests_total",
        "Counts the http requests"
    )
        .labelNames("method", "code")
        .register()

    val httpRequestDuration = Histogram.build(
        "http_request_duration_seconds",
        "Distribution of http request duration"
    )
        .register()

    intercept(ApplicationCallPipeline.Monitoring) {
        val timer = httpRequestDuration.startTimer()
        try {
            logger.info("incoming method=${call.request.httpMethod.value} uri=${call.request.uri}")
            proceed()
        } catch (err: Throwable) {
            logger.info("exception thrown during processing: ${err.message}", err)
            throw err
        } finally {
            timer.observeDuration()
        }
    }

    sendPipeline.intercept(ApplicationSendPipeline.After) { message ->
        val status = call.response.status() ?: (when (message) {
            is OutgoingContent -> message.status
            is HttpStatusCode -> message
            else -> null
        } ?: HttpStatusCode.OK).also { status ->
            call.response.status(status)
        }

        logger.info("responding with status=${status.value}")
        httpRequestCounter.labels(call.request.httpMethod.value, "${status.value}").inc()
    }
}