package com.stripe.rainier.plot

import almond.interpreter.api._
import com.cibo.evilplot.geometry._
import com.cibo.evilplot.plot._
import com.cibo.evilplot.numeric._
import com.cibo.evilplot.colors._
import com.cibo.evilplot.plot.renderers._
import com.cibo.evilplot.plot.aesthetics._

object Jupyter {
  val font =
    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment.getAvailableFontFamilyNames
      .find(_.startsWith("Century Schoolbook"))
      .getOrElse("Arial")

  implicit val extent = Extent(400, 400)
  implicit val theme = Theme(
    fonts = DefaultTheme.DefaultFonts.copy(
      tickLabelSize = 11,
      labelSize = 12,
      fontFace = font
    ),
    colors = DefaultTheme.DefaultColors.copy(
      point = HSLA(211, 38, 48, 0.5),
      fill = HSLA(210, 100, 0, 0.2)
    ),
    elements = DefaultTheme.DefaultElements.copy(
      strokeWidth = 0.5,
      pointSize = 3
    )
  )

  def density[N](seq: Seq[N], minX: Double, maxX: Double)(
      implicit num: Numeric[N],
      theme: Theme): Plot =
    density(seq, Some(Bounds(minX, maxX)))

  def density[N](seq: Seq[N], bounds: Option[Bounds] = None)(
      implicit num: Numeric[N],
      theme: Theme): Plot =
    Histogram(seq.map { n =>
      num.toDouble(n)
    }, binningFunction = Histogram.density, xbounds = bounds)

  def scatter[M, N](seq: Seq[(M, N)])(implicit mNum: Numeric[M],
                                      nNum: Numeric[N],
                                      theme: Theme): Plot =
    ScatterPlot(seq.map { p =>
      Point(mNum.toDouble(p._1), nNum.toDouble(p._2)),
    })

  def scatter[M, N](seq: Seq[(M, N)], color: Color)(implicit mNum: Numeric[M],
                                                    nNum: Numeric[N],
                                                    theme: Theme): Plot =
    ScatterPlot(seq.map { p =>
      Point(mNum.toDouble(p._1), nNum.toDouble(p._2)),
    }, Some(PointRenderer.default[Point](Some(color))))

  def contour[M, N](seq: Seq[(M, N)])(implicit mNum: Numeric[M],
                                      nNum: Numeric[N],
                                      theme: Theme): Plot =
    ContourPlot(seq.map { p =>
      Point(mNum.toDouble(p._1), nNum.toDouble(p._2))
    }, surfaceRenderer = Some(SurfaceRenderer.contours()))

  def line[M, N](seq: Seq[(M, N)])(implicit mNum: Numeric[M],
                                   nNum: Numeric[N],
                                   theme: Theme): Plot =
    LinePlot(seq.map {
      case (m, n) =>
        Point(mNum.toDouble(m), nNum.toDouble(n))
    })

  def line(xbounds: Bounds)(fn: Double => Double)(implicit theme: Theme): Plot =
    lines(xbounds) { x =>
      List(fn(x))
    }

  def lines(xbounds: Bounds)(fn: Double => Seq[Double])(
      implicit theme: Theme): Plot =
    Overlay.fromSeq(fn(0.0).zipWithIndex.toList.map {
      case (_, i) =>
        FunctionPlot.series(x => fn(x)(i),
                            "",
                            theme.colors.trendLine,
                            Some(xbounds))
    })

  def shade[M, N](intervals: Seq[(M, (N, N))])(implicit mNum: Numeric[M],
                                               nNum: Numeric[N]): Plot = {
    val doubleTriples = intervals
      .map {
        case (m, (n1, n2)) =>
          (mNum.toDouble(m), nNum.toDouble(n1), nNum.toDouble(n2))
      }
      .sortBy(_._1)

    val minX = doubleTriples.map(_._1).min
    val maxX = doubleTriples.map(_._1).max
    val minY = doubleTriples.map(_._2).min
    val maxY = doubleTriples.map(_._3).max

    Plot(
      Bounds(minX, maxX),
      Bounds(minY, maxY),
      new PlotRenderer {
        def render(plot: Plot, plotExtent: Extent)(implicit theme: Theme) = {
          val xtransformer = plot.xtransform(plot, plotExtent)
          val ytransformer = plot.ytransform(plot, plotExtent)

          val points = doubleTriples.map {
            case (x, y1, _) =>
              Point(xtransformer(x), ytransformer(y1))
          } ++ doubleTriples.reverse.map {
            case (x, _, y2) =>
              Point(xtransformer(x), ytransformer(y2))
          }

          Polygon(points).filled(theme.colors.fill)
        }
      }
    )
  }

  def show(xLabel: String, yLabel: String, plots: Plot*)(
      implicit extent: Extent,
      theme: Theme,
      oh: OutputHandler): Unit =
    show(
      Overlay
        .fromSeq(plots)
        .xAxis()
        .yAxis()
        .frame()
        .xLabel(xLabel)
        .yLabel(yLabel))

  def show(xLabel: String, plots: Plot*)(implicit extent: Extent,
                                         theme: Theme,
                                         oh: OutputHandler): Unit =
    show(
      Overlay
        .fromSeq(plots)
        .xAxis()
        .frame()
        .xLabel(xLabel))

  def show(plot: Plot)(implicit extent: Extent,
                       theme: Theme,
                       oh: OutputHandler): Unit =
    DisplayData
      .png(renderBytes(plot))
      .show()

  def renderBytes(plot: Plot)(implicit extent: Extent,
                              theme: Theme): Array[Byte] = {
    val baos = new java.io.ByteArrayOutputStream
    val bi = plot.render(extent).asBufferedImage
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
}
