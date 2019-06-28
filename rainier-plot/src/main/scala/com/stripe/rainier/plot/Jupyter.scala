package com.stripe.rainier.plot

import almond.interpreter.api._
import com.cibo.evilplot.geometry._
import com.cibo.evilplot.plot._
import com.cibo.evilplot.numeric._
import com.cibo.evilplot.colors._
import com.cibo.evilplot.plot.renderers._
import com.cibo.evilplot.plot.aesthetics.DefaultTheme._

object Jupyter {    
  implicit val extent = Extent(400, 400)

  def traces(out: Seq[Map[String, Double]],
             truth: Map[String, Double] = Map(),
             lagMax: Int = 40,
              numBars: Int = 50)(implicit outputHandler: OutputHandler): Unit = {
      implicit val extent = Extent(1200, out.head.keys.size * 300.0)
      show(EvilTracePlot.traces(out, truth, lagMax, numBars))
  }

  def pairs(out: Seq[Map[String, Double]],
            truth: Map[String, Double] = Map(),
            numBars: Int = 30)(implicit outputHandler: OutputHandler): Unit = {
      implicit val extent =
        Extent(
          out.head.keys.size * 300.0,
          out.head.keys.size * 300.0)
      show(EvilTracePlot.pairs(out, truth, numBars))
  }

  def histogram[N](seq: Seq[N], numBars: Int = 40)(
    implicit num: Numeric[N], oh: OutputHandler, extent: Extent): Unit = {
      show(Histogram(seq.map{n => num.toDouble(n)}, numBars)
            .xAxis()
            .yAxis()
            .frame()
            .xLabel("x")
      .yLabel("Frequency"))
  }

  def scatter[N](seq: Seq[(N,N)], xLabel: String = "x", yLabel: String = "y")(
    implicit num: Numeric[N], oh: OutputHandler, extent: Extent): Unit = 
    show(ScatterPlot(
      seq.map{p => Point(num.toDouble(p._1), num.toDouble(p._2))},
      pointRenderer =
        Some(PointRenderer.default[Point](Some(HSLA(210, 100, 56, 0.3)), Some(2))))
      .xAxis()
      .yAxis()
      .frame()
      .xLabel(xLabel)
      .yLabel(yLabel))

  def scatter(seq: Seq[Map[String,Double]], xKey: String, yKey: String)(implicit oh: OutputHandler, extent: Extent): Unit = 
    scatter(seq.map{s => (s(xKey), s(yKey))}, xKey, yKey)

  def contour[N](seq: Seq[(N,N)], xLabel: String = "x", yLabel: String = "y")(
    implicit num: Numeric[N], oh: OutputHandler, extent: Extent): Unit = 
      show(ContourPlot(
        seq.map{p => Point(num.toDouble(p._1), num.toDouble(p._2))},
        surfaceRenderer = Some(SurfaceRenderer.contours(
            color = Some(HTMLNamedColors.dodgerBlue))
            ))
        .xAxis()
        .yAxis()
        .frame()
        .xLabel(xLabel)
        .yLabel(yLabel))
  
  def contour(seq: Seq[Map[String,Double]], xKey: String, yKey: String)(implicit oh: OutputHandler, extent: Extent): Unit = 
    contour(seq.map{s => (s(xKey), s(yKey))}, xKey, yKey)
  
  private def show(p: Plot)(implicit oh: OutputHandler, extent: Extent): Unit =
      show(List(List(p)))
    
  private def show(plots: List[List[Plot]])(implicit oh: OutputHandler, extent: Extent): Unit =
    DisplayData
    .png(renderBytes(plots))
    .show()

  private def renderBytes(
    plots: List[List[Plot]])(implicit extent: Extent): Array[Byte] = {
    val baos = new java.io.ByteArrayOutputStream
    val bi = com.cibo.evilplot.plot.Facets(plots).render(extent).asBufferedImage
    val width = extent.width.toInt
    val height = extent.height.toInt
    //enforce that we actually get the dimensions we asked for
    val bo = new java.awt.image.BufferedImage(width, height, bi.getType)
    val g2d = bo.createGraphics()
    g2d.drawImage(bi.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH), 0, 0, width, height, null)
    g2d.dispose()
    javax.imageio.ImageIO.write(bo,"png", baos)
    val array = baos.toByteArray
    baos.close
    array
  }
}