package rainier.repl

case class DensityPlot(nRows: Int = 20,
                       nColumns: Int = 80,
                       xLabelWidth: Int = 9,
                       yLabelWidth: Int = 9,
                       yLabelEvery: Int = 5,
                       logX: Boolean = false,
                       logY: Boolean = false,
                       logMarkers: Boolean = false,
                       markers: String = "·∘○") {

  def plot2D(points: Seq[(Double, Double)]): Seq[String] = {
    val (xs, ys) = points.unzip
    val (xBucketFn, xLabelFn) = mapping(xs.max, xs.min, nColumns, logX)
    val (yBucketFn, yLabelFn) = mapping(ys.max, ys.min, nRows, logY)

    val cellCounts =
      points
        .groupBy { case (x, y) => (xBucketFn(x), yBucketFn(y)) }
        .map { case (k, v) => k -> v.size.toDouble }

    val markerFn = if (cellCounts.values.max == 1) { x: Double =>
      markers.size - 1
    } else
      mapping(cellCounts.values.max + 1,
              cellCounts.values.min,
              markers.size,
              logMarkers)._1
    val cells = cellCounts.map {
      case (k, v) =>
        k -> markers(markerFn(v)).toString
    }

    plotCells(cells, xLabelFn, yLabelFn)
  }

  def plot1D(points: Seq[Double]): Seq[String] = {
    val (xBucketFn, xLabelFn) = mapping(points.max, points.min, nColumns, logX)
    val xCounts =
      points.groupBy(xBucketFn).map { case (k, v) => k -> v.size.toDouble }
    val (yBucketFn, yLabelFn) = mapping(xCounts.values.max, 0.0, nRows, logY)

    val cells =
      xCounts.flatMap {
        case (x, v) =>
          val y = yBucketFn(v)
          val lower = yLabelFn(y)
          val upper = yLabelFn(y + 1)
          val (markerFn, _) = mapping(upper, lower, markers.size, logY)
          val marker = markers(markerFn(v)).toString
          ((x, y) -> marker) :: 0.until(y).toList.map { i =>
            (x, i) -> markers.last.toString
          }
      }.toMap

    plotCells(cells, xLabelFn, yLabelFn)
  }

  private def plotCells(cells: Map[(Int, Int), String],
                        xLabelFn: Int => Double,
                        yLabelFn: Int => Double) = {
    val rows = 0.to(nRows).toList.map { y =>
      val label =
        if (y % yLabelEvery == 0)
          formatLabel(y, yLabelWidth, false, yLabelFn)
        else
          yPadding
      (label :: "|" :: row(y, cells)).mkString
    }

    (xLabels(xLabelFn) :: xAxis :: rows).reverse
  }

  private def mapping(max: Double,
                      min: Double,
                      n: Int,
                      log: Boolean): (Double => Int, Int => Double) = {
    val eps = math.pow(10, math.floor(math.log((max - min) / n) / math.log(10)))

    val totalDelta = (max - min) + eps

    val bucketFn = { v: Double =>
      val delta = v - min
      if (log)
        math.floor((math.log(delta + 1) / math.log(totalDelta + 1)) * n).toInt
      else
        math.floor(delta / totalDelta * n).toInt
    }

    val labelFn = { b: Int =>
      val minDelta =
        if (log)
          math.exp((b.toDouble / n) * math.log(totalDelta + 1)) - 1
        else
          (b.toDouble / n) * totalDelta
      min + minDelta
    }

    (bucketFn, labelFn)
  }

  private val yPadding = " " * yLabelWidth

  private def row(y: Int, cells: Map[(Int, Int), String]): List[String] =
    0.until(nColumns).toList.map { x =>
      cells.getOrElse((x, y), " ")
    }

  private val xAxis =
    (yPadding :: 0.to(nColumns).toList.map { i =>
      if (i % xLabelWidth == 0) "|" else "-"
    }).mkString

  private val xPadding = " " * (yLabelWidth - (xLabelWidth / 2))
  private def xLabels(fn: Int => Double): String =
    (xPadding :: 0.to(nColumns).by(xLabelWidth).toList.map { x =>
      formatLabel(x, xLabelWidth, true, fn)
    }).mkString

  private def formatLabel(i: Int,
                          width: Int,
                          centered: Boolean,
                          fn: Int => Double): String = {
    val v0 = fn(i)
    val v1 = fn(i + 1)
    val delta = v1 - v0
    val magnitude = math.floor(math.log(delta) / math.log(10)).toInt
    val num = format(v0, magnitude)
    val rightPadding =
      if (centered)
        (width - num.size) / 2
      else
        1
    (" " * (width - num.size - rightPadding)) + num + (" " * rightPadding)
  }

  private def format(v: Double, magnitude: Int): String = {
    if (magnitude < 0) {
      ("%." + -magnitude + "f").format(v)
    } else if (magnitude < 3) {
      val d = math.pow(10, magnitude)
      (math.floor(v / d) * d).toInt.toString
    } else if (magnitude < 6) {
      format(v / 1e3, magnitude - 3) + "k"
    } else if (magnitude < 9) {
      format(v / 1e6, magnitude - 6) + "M"
    } else if (magnitude < 12) {
      format(v / 1e9, magnitude - 9) + "B"
    } else if (magnitude < 15) {
      format(v / 1e12, magnitude - 12) + "T"
    } else {
      "%g".format(v)
    }
  }
}
