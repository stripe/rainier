package com.stripe.rainier.compute

import com.stripe.rainier.ir._

class Viz {
  import GraphViz._
  val gv = new GraphViz

  private def opLabel(op: UnaryOp): String =
    op match {
      case ExpOp       => "exp"
      case LogOp       => "log"
      case AbsOp       => "abs"
      case RectifierOp => "relu"
      case NoOp        => "nop"
    }

  def registerPlaceholders(map: Map[Variable, Array[Double]]): Unit = ??? /*{
    counter += 1
    val mid = s"m$counter"
    val cols = map.toList
    val colData = cols.map {
      case (_, arr) =>
        arr.take(5).toList.map(double)
    }
    val colIDs = gv.matrix(mid, "data", colData)
    cols.zip(colIDs).foreach {
      case ((v, _), cid) =>
        ids += (v -> cid)
    }
  }*/

  private def constant(value: String): String =
    gv.node(
      label(value),
      shape("circle")
    )

  private var ids = Map.empty[NonConstant, String]

  def traverse(r: Real): String =
    r match {
      case Constant(c) => constant(formatDouble(c.toDouble))
      case Infinity    => constant("∞")
      case NegInfinity => constant("-∞")
      case nc: NonConstant =>
        ids.get(nc) match {
          case Some(id) => id
          case None     => traverseNonConstant(nc)
        }
    }

  private def traverseNonConstant(nc: NonConstant): String = {
    val id = nc match {
      case Unary(original, op) =>
        val origID = traverse(original)
        val id = gv.node(label(opLabel(op)), shape("oval"))
        gv.edge(id, origID)
        id
      case If(test, nz, z) =>
        val testID = traverse(test)
        val nzID = traverse(nz)
        val zID = traverse(z)
        val id = gv.node(label("If"), shape("diamond"))
        gv.edge(id, testID, style("bold"))
        gv.edge(id, nzID, color("green"))
        gv.edge(id, zID, color("red"))
        id
      case Pow(base, exponent) =>
        val baseID = traverse(base)
        val exponentID = traverse(exponent)
        val id = gv.node(label("pow"), shape("oval"))
        gv.edge(id, baseID, style("bold"))
        gv.edge(id, exponentID)
        id
      case LogLine(ax) =>
        coefficients("LogLine", ax, None)
      case l: Line =>
        val b =
          if (l.b == Real.BigZero)
            None
          else
            Some(l.b)
        coefficients("Line", l.ax, b)
      case l: Lookup =>
        val (id, slotIDs) =
          gv.record("Lookup" :: 0.until(l.table.size).map(_.toString).toList)
        val indexID = traverse(l.index)
        gv.edge(slotIDs.head, indexID)
        val tableIDs = l.table.map(traverse)
        slotIDs.tail.zip(tableIDs).foreach {
          case (s, t) => gv.edge(s, t)
        }
        id
      case _: Variable =>
        gv.node(label("θ"), color("green"), shape("doublecircle"))
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
        val xid = traverse(x)
        gv.edge(wid, xid)
    }
    recordID
  }
}
