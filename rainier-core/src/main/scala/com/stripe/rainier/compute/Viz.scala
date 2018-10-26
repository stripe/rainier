package com.stripe.rainier.compute

import com.stripe.rainier.ir._

class Viz {
  private var counter = 0
  private var ids = Map.empty[NonConstant, String]
  val gv = new GraphViz

  private def opLabel(op: UnaryOp): String =
    op match {
      case ExpOp       => "exp"
      case LogOp       => "log"
      case AbsOp       => "abs"
      case RectifierOp => "relu"
      case NoOp        => "x"
    }

  private def id(real: NonConstant): (String, Boolean) =
    ids.get(real) match {
      case Some(id) =>
        (id, true)
      case None =>
        counter += 1
        val id = s"r$counter"
        ids += (real -> id)
        (id, false)
    }

  private def constant(value: String): String = {
    counter += 1
    val id = s"c$counter"
    gv.statement(id, Map("label" -> value))
    id
  }

  def registerPlaceholders(map: Map[Variable, Array[Double]]): Unit = {
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
  }

  def double(c: Double): String =
    "%.2f".format(c)

  def traverse(r: Real): String =
    r match {
      case Constant(c) => constant(double(c.toDouble))
      case Infinity    => constant("inf")
      case NegInfinity => constant("-inf")
      case nc: NonConstant =>
        val (ncID, seen) = id(nc)
        if (!seen) {
          nc match {
            case Unary(original, op) =>
              val origID = traverse(original)
              gv.record(ncID, List((opLabel(op), None), ("x", Some(origID))))
            case If(test, nz, z) =>
              val testID = traverse(test)
              val nzID = traverse(nz)
              val zID = traverse(z)
              gv.record(ncID,
                        List(
                          ("if", None),
                          ("x==0", Some(testID)),
                          ("false", Some(nzID)),
                          ("true", Some(zID))
                        ))
            case Pow(base, exponent) =>
              val baseID = traverse(base)
              val exponentID = traverse(exponent)
              gv.record(ncID,
                        List(
                          ("^", None),
                          ("", Some(baseID)),
                          ("", Some(exponentID))
                        ))
            case LogLine(ax) =>
              gv.record(ncID, coefficients("*", "^", ax))
            case l: Line =>
              val coef = coefficients("+", "*", l.ax)
              if (l.b == Real.BigZero)
                gv.record(ncID, coef)
              else
                gv.record(ncID, coef :+ (double(l.b.toDouble) -> None))
            case l: Lookup =>
              val indexID = traverse(l.index)
              val tableIDs = l.table.map(traverse)
              gv.record(ncID,
                        List(("switch", None), ("x", Some(indexID))) ++
                          tableIDs.zipWithIndex.map {
                            case (t, i) => (i.toString, Some(t))
                          })
            case _: Variable =>
              gv.statement(ncID, Map("label" -> "θ"))
          }
        }
        ncID
    }

  private def coefficients(plusOp: String,
                           timesOp: String,
                           ax: Coefficients): Seq[(String, Option[String])] =
    (plusOp, None) :: ax.toList.zipWithIndex.map {
      case ((x, a), i) =>
        val xID = traverse(x)
        val label =
          if (a == 1.0)
            "x(%d)".format(i)
          else
            "x(%d)%s%s".format(i, timesOp, double(a.toDouble))
        (label, Some(xID))
    }
}
