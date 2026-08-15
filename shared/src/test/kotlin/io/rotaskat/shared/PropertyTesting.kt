package io.rotaskat.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/**
 * Fuehrt einen Property-Test aus.
 *
 * `checkAll` ist suspend und liefert einen `PropertyContext` zurueck. Eine
 * Testmethode mit Rueckgabewert wird von JUnit stillschweigend uebergangen:
 * der Test stuende dann gruen im Report, ohne je gelaufen zu sein. Dieser
 * Wrapper verwirft das Ergebnis und macht aus dem Test wieder einen Test.
 */
fun property(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
