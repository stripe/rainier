package com.stripe.rainier.plot

import almond.interpreter.api._
import com.cibo.evilplot.geometry._
import com.cibo.evilplot.plot._
import com.cibo.evilplot.numeric._
import com.cibo.evilplot.colors._
import com.cibo.evilplot.plot.renderers._
import com.cibo.evilplot.plot.aesthetics._

import com.stripe.rainier.repl.{hdpi, mean}

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
      theme: Theme): Plot = {
    val doubles = seq.map { n =>
      num.toDouble(n)
    }
    val xbounds = bounds.getOrElse(Bounds(doubles.min, doubles.max))
    val bins = Binning.histogramBinsWithBounds(doubles, xbounds)
    Histogram.fromBins(bins)
  }

  def scatter[M, N](seq: Seq[(M, N)])(implicit mNum: Numeric[M],
                                      nNum: Numeric[N],
                                      theme: Theme): Plot =
    ScatterPlot(seq.map { p =>
      Point(mNum.toDouble(p._1), nNum.toDouble(p._2))
    })

  def scatter[M, N](seq: Seq[(M, N)], color: Color)(implicit mNum: Numeric[M],
                                                    nNum: Numeric[N],
                                                    theme: Theme): Plot =
    ScatterPlot(seq.map { p =>
      Point(mNum.toDouble(p._1), nNum.toDouble(p._2))
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

  def whiskers(samples: Seq[Map[String, Double]]): Plot = {
    val labels = samples.head.keys.toList
    val seq = labels
      .map { k =>
        val dist = samples.map(_(k))
        val (low, high) = hdpi(dist)
        val stats =
          BoxPlotSummaryStatistics(
            dist.min,
            dist.max,
            dist.min,
            dist.max,
            low,
            mean(dist),
            high,
            Nil,
            dist
          )
        k -> stats
      }
      .sortBy(_._2.middleQuantile)

    whiskers(seq)
  }

  def whiskers[K](seq: Seq[(K, BoxPlotSummaryStatistics)])(
      implicit theme: Theme): Plot = {
    val labels = seq.map(_._1.toString)
    val stats = seq.map(_._2)

    val xb = Bounds(0, seq.size.toDouble)
    val yb = Bounds(
      stats.map(_.min).min,
      stats.map(_.max).max
    )

    Plot(
      xb,
      yb,
      BoxPlotRenderer(
        stats.zipWithIndex.map {
          case (s, i) => Some(BoxRenderer.BoxRendererContext(s, i))
        },
        BoxRenderer.custom { (extent, ctx) =>
          val stats = ctx.summaryStatistics
          val scale = extent.height / (stats.upperWhisker - stats.lowerWhisker)

          val topDashes = stats.upperWhisker - stats.upperQuantile
          val topSolid = stats.upperQuantile - stats.middleQuantile
          val bottomSolid = stats.middleQuantile - stats.lowerQuantile
          val bottomDashes = stats.lowerQuantile - stats.lowerWhisker
          val dot = stats.upperWhisker - stats.middleQuantile

          Align
            .center(
              StrokeStyle(Line(scale * topDashes, 2), Clear)
                .rotated(90)
                .translate(extent.width / 2),
              StrokeStyle(Line(scale * topSolid, 2), theme.colors.path)
                .rotated(90)
                .translate(extent.width / 2 - 1.5),
              StrokeStyle(Line(scale * bottomSolid, 2), theme.colors.path)
                .rotated(90)
                .translate(extent.width / 2 - 1.5),
              StrokeStyle(Line(scale * bottomDashes, 2), Clear)
                .rotated(90)
                .translate(extent.width / 2)
            )
            .reduce(_ above _)
            .behind(
              Disc.centered(3).translate(extent.width / 2.0, (dot * scale)))
        },
        PointRenderer.empty[BoxPlotPoint],
        theme.elements.boxSpacing,
        None
      )
    ).xAxis(labels)
      .yAxis()
      .hline(0.0, theme.colors.gridLine, 1)
      .xGrid(
        lineCount = Some(seq.size),
        lineRenderer = Some(new GridLineRenderer {
          def render(extent: Extent, label: String): Drawable = {
            Line(extent.height, theme.elements.gridLineSize)
              .colored(theme.colors.gridLine)
              .dashed(5)
              .rotated(90)
              .translate(extent.width / seq.size)
          }
        })
      )
      .frame()
  }

  def shade[M, N](intervals: Seq[(M, (N, N))])(implicit mNum: Numeric[M],
                                               nNum: Numeric[N],
                                               theme: Theme): Plot = {
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
        def render(plot: Plot, plotExtent: Extent)(implicit _theme: Theme) = {
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
