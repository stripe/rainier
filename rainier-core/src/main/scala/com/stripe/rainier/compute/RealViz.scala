package com.stripe.rainier.compute

import com.stripe.rainier.ir._

class RealViz {
  import GraphViz._
  val gv = new GraphViz

  private def opLabel(op: UnaryOp): String =
    op match {
      case ExpOp => "exp"
      case LogOp => "ln"
      case AbsOp => "abs"
      case NoOp  => "nop"
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

  private var ids = Map.empty[NonConstant, String]

  def traverse(r: Real): String =
    idOrLabel(r) match {
      case Left(id) => id
      case Right(l) =>
        gv.node(
          label(l),
          shape("circle")
        )
    }

  private def idOrLabel(r: Real): Either[String, String] =
    r match {
      case nc: NonConstant => Left(traverseNonConstant(nc))
      case Constant(c)     => Right(formatDouble(c.toDouble))
      case Infinity        => Right("∞")
      case NegInfinity     => Right("-∞")
    }

  private def traverseNonConstant(nc: NonConstant): String =
    ids.get(nc) match {
      case Some(id) => id
      case None =>
        val id = nc match {
          case Unary(original, op) =>
            val origID = traverseNonConstant(original)
            val id = gv.node(label(opLabel(op)), shape("oval"))
            gv.edge(id, origID)
            id
          case Pow(base, exponent) =>
            binary("⬆", base, exponent)
          case Compare(left, right) =>
            binary("⟺", left, right)
          case LogLine(ax) =>
            coefficients("Π↑", ax, None)
          case l: Line =>
            val b =
              if (l.b == Real.BigZero)
                None
              else
                Some(l.b)
            coefficients("Σπ", l.ax, b)
          case l: Lookup =>
            val tableEs = l.table.toList.map(idOrLabel)
            val labels = tableEs.map(_.getOrElse(""))
            val (id, slotIDs) = gv.record("𝑖" :: labels)
            val indexID = traverseNonConstant(l.index)
            gv.edge(slotIDs.head, indexID)
            slotIDs.tail.zip(tableEs).foreach {
              case (s, Left(id)) => gv.edge(s, id)
              case _             => ()
            }
            id
          case _: Variable =>
            gv.node(label("θ"), color("green"), shape("doublecircle"))
        }
        ids += (nc -> id)
        id
    }

  private def binary(op: String, left: Real, right: Real): String = {
    val leftE = idOrLabel(left)
    val rightE = idOrLabel(right)
    val labels =
      List(leftE.getOrElse(""), op, rightE.getOrElse(""))

    val (id, slotIDs) = gv.record(labels)

    leftE.swap.foreach { leftID =>
      gv.edge(slotIDs(0), leftID)
    }
    rightE.swap.foreach { rightID =>
      gv.edge(slotIDs(2), rightID)
    }
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
