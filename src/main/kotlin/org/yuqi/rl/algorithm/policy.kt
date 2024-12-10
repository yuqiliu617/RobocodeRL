package org.yuqi.rl.algorithm

import kotlin.random.Random

typealias Policy<T> = (List<T>) -> Int

val greedy: Policy<Float> = { values -> values.indices.maxBy { values[it] } }

fun lambdaGreedy(ɛ: Float = 0F): Policy<Float> = { values -> if (Random.nextFloat() < ɛ) Random.nextInt(0, values.size) else greedy(values) }

fun noiseGreedy(range: OpenEndRange<Float>): Policy<Float> = { values -> values.map { it + range.start + Random.nextFloat() * (range.endExclusive - range.start) }.let(greedy) }
fun noiseGreedy(max: Float): Policy<Float> = noiseGreedy(0F..<max)