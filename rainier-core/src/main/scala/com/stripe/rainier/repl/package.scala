package com.stripe.rainier

import com.stripe.rainier.sampler._
import com.stripe.rainier.core._
import java.io._

package object repl {
  def plot1D[N](seq: Seq[N])(implicit num: Numeric[N]): Unit = {
    println(DensityPlot().plot1D(seq.map(num.toDouble)).mkString("\n"))
  }

  def plot2D[M, N](seq: Seq[(M, N)])(implicit n: Numeric[N],
                                     m: Numeric[M]): Unit = {
    println(
      DensityPlot()
        .plot2D(seq.map { case (a, b) => (m.toDouble(a), n.toDouble(b)) })
        .mkString("\n"))
  }

  def writeCSV(path: String, seq: Seq[Map[String, Double]]): Unit = {
    val fieldNames = seq.map(_.keys.toSet).reduce(_ ++ _).toList
    val pw = new PrintWriter(new File(path))
    pw.write(fieldNames.mkString(","))
    seq.foreach { row =>
      pw.write("\n")
      fieldNames.tail.foreach { f =>
        pw.write(row.get(f).map(_.toString).getOrElse(""))
        pw.write(",")
      }
      pw.write(row.get(fieldNames.head).map(_.toString).getOrElse(""))
    }
    pw.close
  }

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

  def precis(samples: Seq[Map[String, Double]], corr: Boolean = false): Unit = {
    val meansSDs = computeParamStats(samples)
    val keys = meansSDs.keys

    val correlations = keys.flatMap { k =>
      val diffs = samples.map(_(k) - meansSDs(k)._1)
      keys.map { j =>
        val diffs2 = samples.map(_(j) - meansSDs(j)._1)
        val sumDiffProd = diffs.zip(diffs2).map { case (a, b) => a * b }.sum
        val r = sumDiffProd / (meansSDs(k)._2 * meansSDs(j)._2 * (samples.size - 1))
        (k, j) -> r
      }
    }.toMap

    val cis = keys.map { k =>
      val data = samples.map(_(k)).sorted
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

    keys.foreach { k =>
      val corrValues = if (corr) keys.map { j =>
        correlations(k -> j)
      } else Nil
      println(
        k.padTo(maxKeyLength, ' ') +
          meansSDs(k)._1.formatted("%10.2f") +
          meansSDs(k)._2.formatted("%10.2f") +
          cis(k)._1.formatted("%10.2f") +
          cis(k)._2.formatted("%10.2f") +
          corrValues.map(_.formatted("%7.2f")).mkString(" "))
    }
  }

  def coeftab(models: (String, Seq[Map[String, Double]])*): Unit = {
    val coefs = models.map {
      case (_, samples) =>
        coef(samples)
    }

    val modelNames = models.map(_._1)
    val valWidth = 10.max(modelNames.map(_.size).max)

    val keys = models.flatMap(_._2.head.keys).toSet
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
    computeParamStats(samples).map { case (k, v) => k -> v._1 }

  private def computeParamStats(
      samples: Seq[Map[String, Double]]): Map[String, (Double, Double)] = {
    val keys = samples.head.keys.toList

    keys.map { k =>
      val data = samples.map(_(k))
      val mean = data.sum / data.size
      val stdDev = math.sqrt(data.map { x =>
        math.pow(x - mean, 2)
      }.sum / data.size)
      (k, (mean, stdDev))
    }.toMap
  }

  private def leftPad(s: String, len: Int, elem: Char): String =
    s.reverse.padTo(len, elem).reverse

  def compare(models: (String, RandomVariable[_])*): Unit =
    compare(models, HMC(5), 100000, 10000, 1)
  
  def compare(models: Seq[(String, RandomVariable[_])], sampler: Sampler, warmupIterations: Int, iterations: Int, keepEvery: Int = 1): Unit = {
    val waics = models
      .map { case (s, m) => s -> m.waic(sampler, warmupIterations, iterations, keepEvery) }
      .sortBy(_._2.value)
    val minWaic = waics.head._2.value
    val probs = waics.map {
      case (_, w) => Math.exp((w.value - minWaic) / -2.0)
    }
    val totalProb = probs.sum
    val weights = probs.map(_ / totalProb)
    val maxKeyLength = waics.map(_._1.size).max

    println(
      "".padTo(maxKeyLength, ' ') +
        "WAIC".formatted("%10s") +
        "pWAIC".formatted("%10s") +
        "dWAIC".formatted("%10s") +
        "Weight".formatted("%10s"))

    waics.zip(weights).foreach {
      case ((str, waic), weight) =>
        println(
          str.padTo(maxKeyLength, ' ') +
            waic.value.formatted("%10.2f") +
            waic.pWAIC.formatted("%10.2f") +
            (waic.value - minWaic).formatted("%10.2f") +
            weight.formatted("%10.2f"))
    }
  }

  implicit val rng: RNG = RNG.default
}
