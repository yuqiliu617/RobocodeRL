package org.yuqi.rl.algorithm

import kotlin.random.Random

typealias Policy<T> = (Iterable<T>) -> IndexedValue<T>

val greedy: Policy<Float> = { values -> values.withIndex().maxBy { it.value } }

fun lambdaGreedy(ɛ: Float = 0F): Policy<Float> = { values ->
    if (Random.nextFloat() > ɛ)
        greedy(values)
    else {
        val count = (values as? Collection<Float>)?.size ?: values.count()
        val idx = Random.nextInt(count)
        val value = (values as? List<Float>)?.get(idx) ?: values.elementAt(idx)
        IndexedValue(idx, value)
    }
}

fun noiseGreedy(range: OpenEndRange<Float>): Policy<Float> = { values -> values.map { it + range.start + Random.nextFloat() * (range.endExclusive - range.start) }.let(greedy) }
fun noiseGreedy(max: Float): Policy<Float> = noiseGreedy(0F..<max)