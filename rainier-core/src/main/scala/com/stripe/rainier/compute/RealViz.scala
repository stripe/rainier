package com.stripe.rainier.compute

import com.stripe.rainier.ir._

private class RealViz {
  import GraphViz._
  val gv = new GraphViz

  private var ids = Map.empty[NonConstant, String]

  def output(name: String,
             r: Real,
             gradVars: List[Variable],
             placeholders: Map[Variable, Array[Double]]): Unit = {
    output(name, r, placeholders)
    if (!gradVars.isEmpty) {
      Gradient.derive(gradVars, r).zipWithIndex.foreach {
        case (g, i) =>
          output(name + s"_grad$i", g, Map.empty)
      }
    }
  }

  def output(name: String,
             r: Real,
             placeholders: Map[Variable, Array[Double]]): Unit = {
    if (!placeholders.isEmpty)
      registerPlaceholders(placeholders)
    val id = idOrLabel(r) match {
      case Left(id) => id
      case Right(l) =>
        gv.node(
          label(l),
          shape("square")
        )
    }
    val oid = gv.node(label(name), shape("house"))
    gv.edge(oid, id)
    gv.rank("sink", List(oid))
  }

  private def registerPlaceholders(map: Map[Variable, Array[Double]]): Unit =
    gv.cluster(label("X"), justify("l")) {
      val cols = map.toList
      val colData = cols.map {
        case (_, arr) =>
          arr.take(5).toList.map(formatDouble)
      }
      val colIDs = colData.map { d =>
        val (id, _) = gv.record(true, d)
        id
      }
      cols.zip(colIDs).foreach {
        case ((v, _), cid) =>
          ids += (v -> cid)
      }
    }

  private def idOrLabel(r: Real): Either[String, String] =
    r match {
      case nc: NonConstant => Left(nonConstant(nc))
      case Constant(c)     => Right(formatDouble(c.toDouble))
      case Infinity        => Right("∞")
      case NegInfinity     => Right("-∞")
    }

  private def nonConstant(nc: NonConstant): String =
    ids.get(nc) match {
      case Some(id) => id
      case None =>
        val id = nc match {
          case Unary(original, op, _) =>
            val origID = nonConstant(original)
            val id = gv.node(label(IRViz.opLabel(op)), shape("oval"))
            gv.edge(id, origID)
            id
          case Pow(base, exponent, _) =>
            gv.binaryRecord(IRViz.opLabel(PowOp),
                            idOrLabel(base),
                            idOrLabel(exponent))
          case Compare(left, right) =>
            gv.binaryRecord(IRViz.opLabel(CompareOp),
                            idOrLabel(left),
                            idOrLabel(right))
          case LogLine(ax, _) =>
            coefficients("∏^", ax, None)
          case l: Line =>
            val b =
              if (l.b == Real.BigZero)
                None
              else
                Some(l.b)
            coefficients("∑*", l.ax, b)
          case l: Lookup =>
            val tableEs = l.table.toList.map(idOrLabel)
            val labels = tableEs.map(_.right.getOrElse(""))
            val (id, slotIDs) = gv.record("⋲" :: labels)
            val indexID = nonConstant(l.index)
            gv.edge(slotIDs.head, indexID)
            slotIDs.tail.zip(tableEs).foreach {
              case (s, Left(id)) => gv.edge(s, id)
              case _             => ()
            }
            id
          case _: Variable =>
            gv.node(label("θ"), shape("doublecircle"))
        }
        ids += (nc -> id)
        id
    }

  private def coefficients(name: String,
                           ax: Coefficients,
                           b: Option[BigDecimal]): String = {
    val (xs, as) = ax.toList.unzip
    val vals = (as ++ b.toList).map { a =>
      formatDouble(a.toDouble)
    }
    val (recordID, weightIDs) = gv.record(name :: vals)
    weightIDs.tail.take(xs.size).zip(xs).foreach {
      case (wid, x) =>
        val xid = nonConstant(x)
        gv.edge(wid, xid)
    }
    recordID
  }
}

object RealViz {
  def apply(reals: (String, Real)*): GraphViz =
    apply(reals.toList.map {
      case (s, r) => (s, r, Map.empty[Variable, Array[Double]])
    }, Nil)

  def apply(reals: List[(String, Real, Map[Variable, Array[Double]])],
            gradVars: List[Variable]): GraphViz = {
    val v = new RealViz
    reals.foreach {
      case (name, real, placeholders) =>
        v.output(name, real, gradVars, placeholders)
    }
    v.gv
  }

  def ir(reals: List[(String, Real)],
         variables: List[Variable],
         gradient: Boolean,
         methodSizeLimit: Option[Int]): GraphViz = {
    val withGrad =
      if (gradient)
        reals.flatMap {
          case (name, real) => Compiler.withGradient(name, real, variables)
        } else
        reals
    val translator = new Translator
    val exprs = withGrad.map { case (n, r) => n -> translator.toExpr(r) }
    IRViz(exprs, variables.map(_.param), methodSizeLimit)
  }
}
