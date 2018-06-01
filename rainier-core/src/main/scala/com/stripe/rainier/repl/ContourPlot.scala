package com.stripe.rainier.repl

import com.cibo.evilplot.geometry.Drawable
import com.cibo.evilplot.numeric.Point

object ContourPlot {

  def apply(points: Seq[Point], contours: Int = 5): Drawable = {

    import com.cibo.evilplot.plot.aesthetics.DefaultTheme.defaultTheme
    com.cibo.evilplot.plot
      .ContourPlot(points, contours = Some(contours))
      .xAxis()
      .yAxis()
      .frame()
      .render()
  }

}
