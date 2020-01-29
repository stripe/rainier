package com.stripe.rainier.compute

import com.stripe.rainier.ir.GraphViz

private class BoundsViz {
  import GraphViz._
  val gv = new GraphViz

  private var ids = Map.empty[NonConstant, String]

  def output(name: String, r: Real): Unit = {
    val id = real(r)
    val oid = gv.node(label(name), shape("house"))
    gv.edge(oid, id)
    gv.rank("sink", List(oid))
  }

  private def idOrLabel(r: Real): Either[String, String] =
    r match {
      case nc: NonConstant => Left(nonConstant(nc))
      case c: Constant     => Right(formatBounds(c))
    }

  private def formatBounds(r: Real): String = {
    val l = formatDouble(r.bounds.lower)
    val u = formatDouble(r.bounds.upper)
    if (l == u)
      l
    else
      s"($l,$u)"
  }

  private def real(r: Real): String =
    idOrLabel(r) match {
      case Left(id) => id
      case Right(l) =>
        gv.node(
          label(l),
          shape("square")
        )
    }

  private def nonConstant(nc: NonConstant): String =
    ids.get(nc) match {
      case Some(id) => id
      case None =>
        val id = nc match {
          case Unary(original, _) =>
            val origID = nonConstant(original)
            val id =
              gv.node(label(formatBounds(nc)), shape("oval"))
            gv.edge(id, origID)
            id
          case Pow(base, exponent) =>
            gv.binaryRecord(formatBounds(nc),
                            idOrLabel(base),
                            idOrLabel(exponent))
          case Compare(left, right) =>
            gv.binaryRecord(formatBounds(nc), idOrLabel(left), idOrLabel(right))
          case LogLine(ax) =>
            coefficients(formatBounds(nc), ax, None)
          case l: Line =>
            val b =
              if (l.b.isZero)
                None
              else
                Some(l.b)
            coefficients(formatBounds(nc), l.ax, b)
          case l: Lookup =>
            val tableEs = l.table.toList.map(idOrLabel)
            val labels = tableEs.map(_.right.getOrElse(""))
            val (id, slotIDs) = gv.record(formatBounds(nc) :: labels)
            val indexID = real(l.index)
            gv.edge(slotIDs.head, indexID)
            slotIDs.tail.zip(tableEs).foreach {
              case (s, Left(id)) => gv.edge(s, id)
              case _             => ()
            }
            id
          case _: Parameter =>
            gv.node(label(formatBounds(nc)), shape("doublecircle"))
        }
        ids += (nc -> id)
        id
    }

  private def coefficients(name: String,
                           ax: Coefficients,
                           b: Option[Constant]): String = {
    val (xs, as) = ax.toList.unzip
    val vals = (as ++ b.toList).map(formatBounds)
    val (recordID, weightIDs) = gv.record(name :: vals)
    weightIDs.tail.take(xs.size).zip(xs).foreach {
      case (wid, x) =>
        val xid = nonConstant(x)
        gv.edge(wid, xid)
    }
    recordID
  }
}

object BoundsViz {
  def apply(reals: (String, Real)*): GraphViz =
    apply(reals.toList)

  def apply(reals: List[(String, Real)]): GraphViz = {
    val v = new BoundsViz
    reals.foreach {
      case (name, real) =>
        v.output(name, real)
    }
    v.gv
  }
}
