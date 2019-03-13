package com.stripe.rainier

import com.stripe.rainier.sampler._
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

  implicit val rng: RNG = RNG.default
}
