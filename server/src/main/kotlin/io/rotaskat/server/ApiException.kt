package io.rotaskat.server

import io.ktor.http.HttpStatusCode

/**
 * Ein Fehler, der genau so beim Client ankommen soll. Alles andere faellt in
 * den 500er-Zweig von StatusPages und wird bewusst NICHT im Klartext
 * ausgeliefert.
 */
class ApiException(
    val status: HttpStatusCode,
    override val message: String,
    val details: List<String> = emptyList(),
) : RuntimeException(message)

fun badRequest(message: String, details: List<String> = emptyList()): Nothing =
    throw ApiException(HttpStatusCode.BadRequest, message, details)

fun notFound(message: String): Nothing =
    throw ApiException(HttpStatusCode.NotFound, message)
