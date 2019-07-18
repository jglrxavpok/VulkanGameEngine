package org.jglrxavpok.engine

import com.astedt.robin.util.concurrency.nursery.Nursery

fun <T> openNursery(scope: Nursery.() -> T) = Nursery.open(scope)