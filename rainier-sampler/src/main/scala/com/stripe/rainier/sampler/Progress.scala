package com.stripe.rainier.sampler

trait Progress {
  def start(chain: Int, stats: Stats): Unit
  def refresh(chain: Int, stats: Stats): Unit
  def finish(chain: Int, stats: Stats): Unit
  def outputEverySeconds: Double
}

object SilentProgress extends Progress {
  def start(chain: Int, stats: Stats): Unit = ()
  def refresh(chain: Int, stats: Stats): Unit = ()
  def finish(chain: Int, stats: Stats): Unit = ()
  val outputEverySeconds = 1e100
}

object ConsoleProgress extends Progress {
  def start(chain: Int, stats: Stats): Unit = ()
  def refresh(chain: Int, stats: Stats): Unit =
    println(s"Chain ${chain}: Iteration ${stats.iterations}")
  def finish(chain: Int, stats: Stats): Unit =
    refresh(chain, stats)
  val outputEverySeconds = 0.5
}
