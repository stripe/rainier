package com.stripe.rainier.plot

import almond.interpreter.api._

object Jupyter {
  def traces(out: Seq[Map[String, Double]],
             truth: Map[String, Double] = Map(),
             lagMax: Int = 40,
             numBars: Int = 50)(implicit outputHandler: OutputHandler): Unit =
    DisplayData
      .png(
        EvilTracePlot.renderBytes(
          EvilTracePlot.traces(out, truth, lagMax, numBars)))
      .show()

  def pairs(out: Seq[Map[String, Double]],
            truth: Map[String, Double] = Map(),
            numBars: Int = 30)(implicit outputHandler: OutputHandler): Unit =
    DisplayData
      .png(
        EvilTracePlot.renderBytes(
          EvilTracePlot.traces(out, truth, numBars)
        )
      )
      .show()
}
