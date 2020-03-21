package com.stripe.rainier.sampler

trait Progress {
  def start(chain: Int): Unit
  def refresh(chain: Int, message: String, stats: Stats, mass: MassMatrix): Unit
  def finish(chain: Int, message: String, stats: Stats, mass: MassMatrix): Unit
  def outputEverySeconds: Double
}

object SilentProgress extends Progress {
  def start(chain: Int): Unit = ()
  def refresh(chain: Int,
              message: String,
              stats: Stats,
              mass: MassMatrix): Unit = ()
  def finish(chain: Int,
             message: String,
             stats: Stats,
             mass: MassMatrix): Unit = ()
  val outputEverySeconds = 1e100
}

object ConsoleProgress extends Progress {
  def start(chain: Int): Unit = ()
  def refresh(chain: Int,
              message: String,
              stats: Stats,
              mass: MassMatrix): Unit = {
    println(s"Chain ${chain} ${message}")
    println(s"Iteration ${stats.iterations}")
    println(s"BFMI: ${stats.bfmi}")
    println(s"Step size: ${stats.stepSizes.mean}")
    println(s"Acceptance rate: ${stats.acceptanceRates.mean}")
  }

  def finish(chain: Int,
             message: String,
             stats: Stats,
             mass: MassMatrix): Unit =
    refresh(chain, message, stats, mass)
  val outputEverySeconds = 0.5
}
