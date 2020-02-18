package com.stripe.rainier

import com.cibo.evilplot.geometry._
import com.cibo.evilplot.plot._
import com.cibo.evilplot.numeric._
import com.cibo.evilplot.colors._
import com.cibo.evilplot.plot.renderers._
import com.cibo.evilplot.plot.aesthetics._
import almond.display.Image
import com.stripe.rainier.sampler._

package object notebook {

  implicit val rng = RNG.default

  import almond.api.JupyterApi
  object PlotThemes {
    private val blueColor = HSLA(211, 38, 48, 0.5)
    private val grayColor = HSLA(210, 100, 0, 0.2)
    private val font =
      java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment.getAvailableFontFamilyNames
        .find(_.startsWith("Century Schoolbook"))
        .getOrElse("Arial")

    val default: Theme = Theme(
      fonts = DefaultTheme.DefaultFonts.copy(
        tickLabelSize = 11,
        labelSize = 12,
        fontFace = font
      ),
      colors = DefaultTheme.DefaultColors.copy(
        bar = blueColor,
        point = blueColor,
        fill = grayColor
      ),
      elements = DefaultTheme.DefaultElements.copy(
        strokeWidth = 0.5,
        pointSize = 3
      )
    )

    val blue: Theme = default.copy(
      colors = default.colors.copy(
        fill = blueColor,
        trendLine = blueColor,
        path = blueColor
      )
    )

    val gray: Theme = default.copy(
      colors = default.colors.copy(
        bar = grayColor,
        point = grayColor,
        trendLine = grayColor,
        path = grayColor
      )
    )
  }

  implicit val extent: Extent = Extent(400, 400)
  implicit val theme: Theme = PlotThemes.default

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

  def show(xLabel: String, yLabel: String, title: String, plots: Plot*)(
      implicit extent: Extent,
      theme: Theme): Image =
    show(
      Overlay
        .fromSeq(plots)
        .xAxis()
        .yAxis()
        .frame()
        .xLabel(xLabel)
        .yLabel(yLabel)
        .title(title))

  def show(xLabel: String, yLabel: String, plots: Plot*)(
      implicit extent: Extent,
      theme: Theme): Image =
    show(
      Overlay
        .fromSeq(plots)
        .xAxis()
        .yAxis()
        .frame()
        .xLabel(xLabel)
        .yLabel(yLabel))

  def show(xLabel: String, plots: Plot*)(implicit extent: Extent,
                                         theme: Theme): Image =
    show(
      Overlay
        .fromSeq(plots)
        .xAxis()
        .frame()
        .xLabel(xLabel))

  def show(plot: Plot)(implicit extent: Extent, theme: Theme): Image =
    Image.fromRenderedImage(plot.render(extent)(theme).asBufferedImage,
                            format = Image.PNG)

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

//Convenience methods modeled after _Statistical Rethinking_
  def loadCSV(path: String,
              delimiter: String = ","): List[Map[String, String]] = {
    val (head :: tail) = scala.io.Source.fromFile(path).getLines.toList
    val headings = head.split(delimiter).map { s =>
      s.stripPrefix("\"").stripSuffix("\"")
    }
    tail.map { line =>
      headings.zip(line.split(delimiter)).toMap
    }
  }

  def hdpi(samples: Seq[Double], prob: Double = 0.89): (Double, Double) = {
    val sorted = samples.sorted.toArray
    val idx = math.ceil(prob * sorted.size).toInt
    if (idx == sorted.size)
      (sorted.head, sorted.last)
    else {
      val cis = 0.until(sorted.size - idx).toList.map { i =>
        val bottom = sorted(i)
        val top = sorted(i + idx)
        val width = top - bottom
        (width, bottom, top)
      }
      val res = cis.minBy(_._1)
      (res._2, res._3)
    }
  }

  def mean[N](seq: Seq[N])(implicit num: Numeric[N]): Double =
    seq.map { n =>
      num.toDouble(n)
    }.sum / seq.size

  def stddev[N](seq: Seq[N])(implicit num: Numeric[N]): Double = {
    val doubles = seq.map { n =>
      num.toDouble(n)
    }
    val mean = doubles.sum / doubles.size
    math.sqrt(doubles.map { x =>
      math.pow(x - mean, 2)
    }.sum / doubles.size)
  }

  def standardize[N](seq: Seq[N])(implicit num: Numeric[N]): Seq[Double] = {
    val m = mean(seq)
    val sd = stddev(seq)
    seq.map { x =>
      (num.toDouble(x) - m) / sd
    }
  }

  def precis(samples: Seq[Traversable[(String, Double)]],
             corr: Boolean = false): Unit = {
    val sampleSeqs = samples.map(_.toSeq)
    val meansSDs = computeParamStats(sampleSeqs)
    val keys = meansSDs.map(_._1)

    val correlations = keys.zipWithIndex.flatMap {
      case (k, i) =>
        val diffs = sampleSeqs.map(_(i)._2 - meansSDs(i)._2._1)
        keys.zipWithIndex.map {
          case (l, j) =>
            val diffs2 = sampleSeqs.map(_(j)._2 - meansSDs(j)._2._1)
            val sumDiffProd = diffs.zip(diffs2).map { case (a, b) => a * b }.sum
            val r = sumDiffProd / (meansSDs(i)._2._2 * meansSDs(j)._2._2 * (sampleSeqs.size - 1))
            (k, l) -> r
        }
    }.toMap

    val cis = keys.zipWithIndex.map {
      case (k, i) =>
        val data = sampleSeqs.map(_(i)._2).sorted
        val low = data(math.floor(data.size * 0.055).toInt)
        val high = data(math.floor(data.size * 0.945).toInt)
        (k, (low, high))
    }.toMap

    val maxKeyLength = keys.map(_.size).max
    val corrKeys = if (corr) keys else Nil
    println(
      "".padTo(maxKeyLength, ' ') +
        "Mean".formatted("%10s") +
        "StdDev".formatted("%10s") +
        "5.5%".formatted("%10s") +
        "94.5%".formatted("%10s") +
        corrKeys.map(_.formatted("%7s")).mkString(" "))

    keys.zipWithIndex.foreach {
      case (k, i) =>
        val corrValues = if (corr) keys.map { j =>
          correlations(k -> j)
        } else Nil
        val (mean, sd) = meansSDs(i)._2
        val (lowCI, highCI) = cis(k)
        println(
          k.padTo(maxKeyLength, ' ') +
            mean.formatted("%10.2f") +
            sd.formatted("%10.2f") +
            lowCI.formatted("%10.2f") +
            highCI.formatted("%10.2f") +
            corrValues.map(_.formatted("%7.2f")).mkString(" "))
    }
  }

  def coeftab(models: (String, Seq[Traversable[(String, Double)]])*): Unit = {
    val coefs = models.map {
      case (_, samples) =>
        coef(samples.map(_.toSeq)).toMap
    }

    val modelNames = models.map(_._1)
    val valWidth = 10.max(modelNames.map(_.size).max)

    val keys = models.flatMap(_._2.head.map(_._1)).distinct
    val maxKeyLength = keys.map(_.size).max

    println(
      "".padTo(maxKeyLength, ' ') +
        modelNames.map(leftPad(_, valWidth, ' ')).mkString("")
    )
    keys.foreach { k =>
      println(
        k.padTo(maxKeyLength, ' ') +
          coefs
            .map(
              _.get(k)
                .map(_.formatted("%10.2f"))
                .getOrElse(leftPad("NA", valWidth, ' '))
            )
            .mkString("")
      )
    }
  }

  def coef(samples: Seq[Map[String, Double]]): Map[String, Double] =
    computeParamStats(samples.map(_.toSeq)).map { case (k, v) => k -> v._1 }.toMap

  def coef(samples: Seq[Seq[(String, Double)]]): Seq[(String, Double)] =
    computeParamStats(samples).map { case (k, v) => k -> v._1 }

  private def computeParamStats(samples: Seq[Seq[(String, Double)]])
    : IndexedSeq[(String, (Double, Double))] = {
    val keys = samples.head.map(_._1).toVector

    keys.zipWithIndex.map {
      case (k, i) =>
        val data = samples.map(_(i)._2)
        val mean = data.sum / data.size
        val stdDev = math.sqrt(data.map { x =>
          math.pow(x - mean, 2)
        }.sum / data.size)
        (k, (mean, stdDev))
    }
  }

  private def leftPad(s: String, len: Int, elem: Char): String =
    s.reverse.padTo(len, elem).reverse

  implicit def progress(implicit kernel: JupyterApi): Progress =
    HTMLProgress(kernel, 0.5)
}
