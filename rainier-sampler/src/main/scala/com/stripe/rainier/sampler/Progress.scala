package com.stripe.rainier.sampler

trait Progress {
  def start(chain: Int, message: String, stats: Stats): Unit
  def refresh(chain: Int, message: String, stats: Stats): Unit
  def finish(chain: Int, message: String, stats: Stats): Unit
  def outputEverySeconds: Double
}

object SilentProgress extends Progress {
  def start(chain: Int, message: String, stats: Stats): Unit = ()
  def refresh(chain: Int, message: String, stats: Stats): Unit = ()
  def finish(chain: Int, message: String, stats: Stats): Unit = ()
  val outputEverySeconds = 1e100
}

object ConsoleProgress extends Progress {
  def start(chain: Int, message: String, stats: Stats): Unit = ()
  def refresh(chain: Int, message: String, stats: Stats): Unit =
    println(s"Chain ${chain} ${message}: Iteration ${stats.iterations}")
  def finish(chain: Int, message: String, stats: Stats): Unit =
    refresh(chain, message, stats)
  val outputEverySeconds = 0.5
}
