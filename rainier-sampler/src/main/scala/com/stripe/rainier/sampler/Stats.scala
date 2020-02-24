package com.stripe.rainier.sampler

class Stats(n: Int) {
  var gradientEvaluations = 0
  var iterations = 0

  val gradientTimes = new RingBuffer(n)
  val iterationTimes = new RingBuffer(n)
  val stepSizes = new RingBuffer(n)
  val acceptanceRates = new RingBuffer(n)
}

class RingBuffer(size: Int) {
  private var i = 0
  private var full = false
  private val buf = new Array[Double](size)

  def add(value: Double): Unit = {
    i += 1
    if (i == size)
      full = true
    i = i % size
    buf(i) = value
  }

  def last: Double = buf(i)
  def toList: List[Double] = {
    val lastPart = buf.take(i).toList
    if (full)
      buf.drop(i).toList ++ lastPart
    else
      lastPart
  }
  def sample()(implicit rng: RNG) = {
    if (full)
      buf(rng.int(size))
    else
      buf(rng.int(i + 1))
  }

  def mean: Double = {
    var sum = 0.0
    var j = 0
    while (j < size) {
      sum += buf(j)
      j += 1
    }
    if (full)
      j / size.toDouble
    else
      j / i.toDouble
  }
}
