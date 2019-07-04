package com.stripe.rainier.plot

import almond.interpreter.api._
import com.cibo.evilplot.geometry._
import com.cibo.evilplot.plot._
import com.cibo.evilplot.numeric._
import com.cibo.evilplot.colors._
import com.cibo.evilplot.plot.renderers._
import com.cibo.evilplot.plot.renderers.SurfaceRenderer.SurfaceRenderContext
import com.cibo.evilplot.plot.aesthetics.DefaultTheme._
import com.cibo.evilplot.plot.aesthetics.Theme
import com.stripe.rainier.repl.hdpi

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
      Extent(out.head.keys.size * 300.0, out.head.keys.size * 300.0)
    show(EvilTracePlot.pairs(out, truth, numBars))
  }

  def histogram[N](seq: Seq[N], numBars: Int = 40)(implicit num: Numeric[N],
                                                   oh: OutputHandler,
                                                   extent: Extent): Unit = {
    show(
      Histogram(seq.map { n =>
        num.toDouble(n)
      }, numBars)
        .xAxis()
        .yAxis()
        .frame()
        .xLabel("x")
        .yLabel("Frequency"))
  }

  private def scatterPlot[N](seq: Seq[(N, N)])(implicit num: Numeric[N]): Plot =
    ScatterPlot(
      seq.map { p =>
        Point(num.toDouble(p._1), num.toDouble(p._2))
      },
      pointRenderer = Some(
        PointRenderer.default[Point](Some(HSLA(210, 100, 56, 0.5)), Some(2))))

  def scatter[N](seq: Seq[(N, N)], xLabel: String = "x", yLabel: String = "y")(
      implicit num: Numeric[N],
      oh: OutputHandler,
      extent: Extent): Unit =
    show(
      scatterPlot(seq)
        .xAxis()
        .yAxis()
        .frame()
        .xLabel(xLabel)
        .yLabel(yLabel))

  def scatter(seq: Seq[Map[String, Double]], xKey: String, yKey: String)(
      implicit oh: OutputHandler,
      extent: Extent): Unit =
    scatter(seq.map { s =>
      (s(xKey), s(yKey))
    }, xKey, yKey)

  def scatterLines(seq: Seq[Map[String, Double]], xKey: String, yKey: String)(
      fn: Double => Seq[Double])(implicit oh: OutputHandler,
                                 extent: Extent): Unit = {
    val scatter = scatterPlot(seq.map { s =>
      (s(xKey), s(yKey))
    })
    val functionPlots = fn(0.0).zipWithIndex.toList.map {
      case (_, i) =>
        FunctionPlot.series(x => fn(x)(i), "", HSLA(0, 100, 56, 0.5), Some(scatter.xbounds))
    }
    show(
      Overlay
        .fromSeq(scatter :: functionPlots)
        .xAxis()
        .yAxis()
        .frame()
        .xLabel(xKey)
        .yLabel(yKey))
  }

  private def shadedRenderer(intervals: Seq[(Double, (Double,Double))]) = new PlotRenderer {
    def render(plot: Plot, plotExtent: Extent)(implicit theme: Theme): Drawable = {

      val xtransformer = plot.xtransform(plot, plotExtent)
      val ytransformer = plot.ytransform(plot, plotExtent)

      val points = intervals.map{case (x, (y1, _)) => 
        Point(xtransformer(x), ytransformer(y1))
      } ++ intervals.reverse.map{case (x, (_, y2)) =>
        Point(xtransformer(x), ytransformer(y2))
      }
      Polygon(points).filled(HSLA(210, 100, 0, 0.2))
    }
  }

  private def median(seq: Seq[Double]): Double = {
    val sorted = seq.toList.sorted
    sorted(seq.size / 2)
  }

  def scatterShaded(seq: Seq[Map[String, Double]], xKey: String, yKey: String)(
    fn: Double => Seq[Double])(implicit oh: OutputHandler,
                               extent: Extent): Unit = {
    val xy = seq.map { s => (s(xKey), s(yKey)) }
    val scatter = scatterPlot(xy)  
  
    val minX = BigDecimal(scatter.xbounds.min)
    val maxX = BigDecimal(scatter.xbounds.max)
    val grid = (maxX - minX) / 20.0
    val xVals = (minX - grid).to(maxX+grid).by(grid).map(_.toDouble).toList
    val intervals = xVals.map{x => (x, hdpi(fn(x)))}

    val shaded = Plot(
      scatter.xbounds,
      scatter.ybounds,
      shadedRenderer(intervals))

    val line = LinePlot.series(intervals.map{case (x, (y1, y2)) => 
        Point(x, y1 + ((y2 - y1)/2))
    }, "", HSLA(210, 100, 0, 0.8))
    
    show(Overlay(shaded, scatter, line)
        .xAxis()
        .yAxis()
        .frame()
        .xLabel(xKey)
        .yLabel(yKey))
  }

  def contour[N](seq: Seq[(N, N)], xLabel: String = "x", yLabel: String = "y")(
      implicit num: Numeric[N],
      oh: OutputHandler,
      extent: Extent): Unit =
    show(
      ContourPlot(
        seq.map { p =>
          Point(num.toDouble(p._1), num.toDouble(p._2))
        },
        surfaceRenderer = Some(
          SurfaceRenderer.contours(color = Some(HTMLNamedColors.dodgerBlue))))
        .xAxis()
        .yAxis()
        .frame()
        .xLabel(xLabel)
        .yLabel(yLabel))

  def contour(seq: Seq[Map[String, Double]], xKey: String, yKey: String)(
      implicit oh: OutputHandler,
      extent: Extent): Unit =
    contour(seq.map { s =>
      (s(xKey), s(yKey))
    }, xKey, yKey)

  private def show(p: Plot)(implicit oh: OutputHandler, extent: Extent): Unit =
    show(List(List(p)))

  private def show(plots: List[List[Plot]])(implicit oh: OutputHandler,
                                            extent: Extent): Unit =
    DisplayData
      .png(renderBytes(plots))
      .show()

  private def renderBytes(plots: List[List[Plot]])(
      implicit extent: Extent): Array[Byte] = {
    val baos = new java.io.ByteArrayOutputStream
    val bi = com.cibo.evilplot.plot.Facets(plots).render(extent).asBufferedImage
    val width = extent.width.toInt
    val height = extent.height.toInt
    //enforce that we actually get the dimensions we asked for
    val bo = new java.awt.image.BufferedImage(width, height, bi.getType)
    val g2d = bo.createGraphics()
    g2d.drawImage(
      bi.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH),
      0,
      0,
      width,
      height,
      null)
    g2d.dispose()
    javax.imageio.ImageIO.write(bo, "png", baos)
    val array = baos.toByteArray
    baos.close
    array
  }

  //copied from private evilplot class
  private case class SurfacePlotRenderer(
    data: Seq[Seq[Seq[Point3]]],
    surfaceRenderer: SurfaceRenderer
  ) extends PlotRenderer {
    // Throw away empty levels.
    private val allLevels = data.flatMap(_.headOption.map(_.headOption.map(_.z).getOrElse(0d)))

    override def legendContext: LegendContext = {
      surfaceRenderer.legendContext(allLevels)
    }

    def render(plot: Plot, plotExtent: Extent)(implicit theme: Theme): Drawable = {
      val xtransformer = plot.xtransform(plot, plotExtent)
      val ytransformer = plot.ytransform(plot, plotExtent)

      data.zipWithIndex
        .withFilter(_._1.nonEmpty)
        .map {
          case (level, index) =>
            val transformedAndCulled = level.map { path =>
              path
                .withFilter { p =>
                  plot.xbounds.isInBounds(p.x) && plot.ybounds.isInBounds(p.y)
                }
                .map(p => Point(xtransformer(p.x), ytransformer(p.y)))
            }
            val levelContext =
              SurfaceRenderContext(allLevels, transformedAndCulled, allLevels(index))
            surfaceRenderer.render(plot, plotExtent, levelContext)
        }
        .group
    }
  }
}
