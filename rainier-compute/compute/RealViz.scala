package com.stripe.rainier.compute

import com.stripe.rainier.ir._

private class RealViz {
  import GraphViz._
  val gv = new GraphViz

  private var ids = Map.empty[NonConstant, String]

  def output(name: String,
             r: Real,
             gradVars: List[Parameter],
             columns: List[Column]): Unit = {
    output(name, r, columns)
    if (!gradVars.isEmpty) {
      Gradient.derive(gradVars, r).zipWithIndex.foreach {
        case (g, i) =>
          output(name + s"_grad$i", g, Nil)
      }
    }
  }

  def output(name: String, r: Real, columns: List[Column]): Unit = {
    if (!columns.isEmpty)
      registerColumns(columns)
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

  private def registerColumns(cols: List[Column]): Unit =
    gv.cluster(label("X"), justify("l")) {
      val colData = cols.map { p =>
        p.values.take(5).map { d =>
          formatDouble(d.toDouble)
        }
      }
      val colIDs = colData.map { d =>
        val (id, _) = gv.record(true, d)
        id
      }
      cols.zip(colIDs).foreach {
        case (v, cid) =>
          ids += (v -> cid)
      }
    }

  private def idOrLabel(r: Real): Either[String, String] =
    r match {
      case nc: NonConstant => Left(nonConstant(nc))
      case Scalar(c)       => Right(formatDouble(c.toDouble))
    }

  private def nonConstant(nc: NonConstant): String =
    ids.get(nc) match {
      case Some(id) => id
      case None =>
        val id = nc match {
          case Unary(original, op) =>
            val origID = nonConstant(original)
            val id = gv.node(label(IRViz.opLabel(op)), shape("oval"))
            gv.edge(id, origID)
            id
          case Pow(base, exponent) =>
            gv.binaryRecord(IRViz.opLabel(PowOp),
                            idOrLabel(base),
                            idOrLabel(exponent))
          case Compare(left, right) =>
            gv.binaryRecord(IRViz.opLabel(CompareOp),
                            idOrLabel(left),
                            idOrLabel(right))
          case LogLine(ax) =>
            coefficients("∏^", ax, None)
          case l: Line =>
            val b =
              if (l.b == Decimal.Zero)
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
          case _: Parameter =>
            gv.node(label("θ"), shape("doublecircle"))
          case _: Column =>
            sys.error("columns should be registered")
        }
        ids += (nc -> id)
        id
    }

  private def coefficients(name: String,
                           ax: Coefficients,
                           b: Option[Decimal]): String = {
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
    apply(reals.toList, Nil)

  def apply(reals: List[(String, Real)],
            gradVars: List[Parameter]): GraphViz = {
    val v = new RealViz
    reals.foreach {
      case (name, real) =>
        val cols =
          RealOps.columns(real)
        v.output(name, real, gradVars, cols.toList)
    }
    v.gv
  }
}
