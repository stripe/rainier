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
      color("yellow"),
      shape("box")
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
        coefficients("purple", "*", "^", ax, None)
      case l: Line =>
        val b =
          if (l.b == Real.BigZero)
            None
          else
            Some(l.b)
        coefficients("blue", "+", "*", l.ax, b)
      case l: Lookup =>
        val indexID = traverse(l.index)
        val tableIDs = l.table.map(traverse)
        val id = gv.node(label("Lookup"), shape("star"))
        gv.edge(id, indexID, style("bold"))
        tableIDs.foreach { t =>
          gv.edge(id, t)
        }
        id
      case _: Variable =>
        gv.node(label("θ"), color("green"), shape("doublecircle"))
    }
    ids += (nc -> id)
    id
  }

  private def coefficients(clr: String,
                           plusOp: String,
                           timesOp: String,
                           ax: Coefficients,
                           b: Option[BigDecimal]): String = {
    val axIDs = ax.toList.map {
      case (x, a) =>
        traverse(x) -> a
    }
    if (axIDs.size == 1 && !b.isDefined) {
      val boxID = coefficient(clr, timesOp, axIDs.head._2)
      gv.edge(boxID, axIDs.head._1)
      boxID
    } else {
      val (id, edges) = gv.cluster(color(clr)) {
        val id = gv.node(label(plusOp), shape("oval"))
        b.foreach { a =>
          gv.edge(id, constant(formatDouble(a.toDouble)))
        }
        (id, axIDs.map {
          case (xid, a) =>
            val boxID = coefficient("grey", timesOp, a)
            gv.edge(id, boxID)
            (boxID, xid)
        })
      }
      edges.foreach { case (boxID, xid) => gv.edge(boxID, xid) }
      id
    }
  }

  private def coefficient(clr: String, timesOp: String, a: BigDecimal): String =
    if (a == Real.BigOne)
      gv.node(label(""), color("grey"), shape("square"))
    else {
      val lbl = "%s%s".format(timesOp, formatDouble(a.toDouble))
      gv.node(label(lbl), color(clr), shape("square"))
    }
}
