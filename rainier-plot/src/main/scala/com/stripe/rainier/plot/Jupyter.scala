package com.stripe.rainier.plot

import almond.interpreter.api._
import com.cibo.evilplot.geometry._

object Jupyter {
  def traces(out: Seq[Map[String, Double]],
             truth: Map[String, Double] = Map(),
             lagMax: Int = 40,
             numBars: Int = 50)(implicit outputHandler: OutputHandler): Unit =
    DisplayData
      .png(
        EvilTracePlot.renderBytes(
          EvilTracePlot.traces(out, truth, lagMax, numBars),
          Extent(1200, out.head.keys.size * 300.0)))
      .show()

  def pairs(out: Seq[Map[String, Double]],
            truth: Map[String, Double] = Map(),
            numBars: Int = 30)(implicit outputHandler: OutputHandler): Unit =
    DisplayData
      .png(
        EvilTracePlot.renderBytes(
          EvilTracePlot.pairs(out, truth, numBars),
          Extent(out.head.keys.size * 300.0, out.head.keys.size * 300.0)))
      .show()
}
