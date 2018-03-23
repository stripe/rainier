package rainier.report

import rainier.compute._
import rainier.core._
import rainier.sampler._

case class Report(chains: Seq[List[Sample]])(
    fn: Numeric[Real] => Map[String, Double]) {
  import Report._

  val acceptanceRates = Summary(chains.map { chain =>
    chain.count(_.accepted).toDouble / chain.size
  })
  val vectors = chains.map { chain =>
    chain.map { c =>
      fn(c.evaluator)
    }
  }
  val keys = vectors.flatten
    .foldLeft(Set.empty[String]) { case (k, v) => k ++ v.keys }
    .toList
  val traces = keys.map { k =>
    (k, vectors.map { l =>
      Summary(l.map { v =>
        v.getOrElse(k, 0.0)
      })
    })
  }.toMap

  val pooledTraces = traces.mapValues { ts =>
    Summary(ts.map(_.list).reduce(_ ++ _))
  }
  val convergenceStats = traces.mapValues { gelmanRubin(_) }

  def printReport(): Unit = {
    println(
      Report.fmtKey("Acceptance rate") + Report.fmtInterval(acceptanceRates))
    println("")
    keys.toList.sorted.foreach { k =>
      val convergence =
        if (chains.size > 1)
          " [" + Report.fmtDouble(convergenceStats(k)) + "]"
        else
          ""
      println(
        Report.fmtKey(k) + Report.fmtInterval(pooledTraces(k)) + convergence)
    }
  }
}

object Report {

  def groupByWalker[A](list: List[A], walkers: Int): Seq[List[A]] = {
    list.zipWithIndex
      .groupBy { case (c, i) => i % walkers }
      .values
      .toList
      .map { l =>
        l.map(_._1)
      }
  }

  def gelmanRubin(traces: Seq[Summary]): Double = {
    val n = traces.head.list.size
    val variances = Summary(traces.map(_.variance))
    val means = Summary(traces.map(_.mean))
    val w = variances.mean
    val b = means.variance
    val v = ((1.0 - (1.0 / n)) * w) + b
    math.sqrt(v / w)
  }

  case class Summary(list: Seq[Double]) {
    val mean = list.sum / list.size
    val variance = list.map { n =>
      math.pow(n - mean, 2)
    }.sum / (list.size - 1)
    val (p5, p95) = {
      val sorted = list.sorted
      (sorted((list.size * 0.05).toInt), sorted((list.size * 0.95).toInt))
    }
  }

  def fmtDouble(r: Double): String = f"$r%6.3f"
  def fmtKey(k: String): String = f"$k%20s: "
  def fmtInterval(s: Summary): String = {
    val mean = Report.fmtDouble(s.mean)
    if (s.list.size > 1)
      mean +
        " (" + Report.fmtDouble(s.p5) +
        "," + Report.fmtDouble(s.p95) + ")"
    else
      mean
  }

  def printReport[T](model: RandomVariable[T],
                     sampler: Sampler = Sampler.default)(
      implicit rng: RNG = RNG.default,
      sampleable: Sampleable[T, Map[String, Double]]): Unit = {
    val (name, config) = sampler.description
    println("")
    println(name)

    println(fmtKey("Variables") + Real.variables(model.density).size)

    config.foreach {
      case (k, v) =>
        println(fmtKey(k) + fmtDouble(v))
    }

    val t1 = System.currentTimeMillis
    val samples = sampler.sample(model.density).toList
    val t2 = System.currentTimeMillis
    val time = (t2 - t1).toDouble / 1000

    println(fmtKey("Run time") + fmtDouble(time))

    val chains =
      samples
        .groupBy(_.chain)
        .values
        .toList

    Report(chains) { num =>
      model.get(rng, sampleable, num)
    }.printReport()
  }
}
