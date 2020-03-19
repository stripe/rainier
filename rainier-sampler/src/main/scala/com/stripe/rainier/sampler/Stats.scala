package com.stripe.rainier.sampler

class Stats(n: Int) {
  var gradientEvaluations = 0L
  var iterations = 0
  var divergences = 0

  val gradientTimes = new RingBuffer(n)
  val iterationTimes = new RingBuffer(n)
  val stepSizes = new RingBuffer(n)
  val acceptanceRates = new RingBuffer(n)
  val gradsPerIteration = new RingBuffer(n)

  val energyVariance = new VarianceEstimator(1)
  var energyTransitions2 = 0.0
  def bfmi = energyTransitions2 / energyVariance.raw(0)
}

class RingBuffer(size: Int) {
  var full = false
  private var i = 0
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
      sum / size.toDouble
    else
      sum / i.toDouble
  }
}
