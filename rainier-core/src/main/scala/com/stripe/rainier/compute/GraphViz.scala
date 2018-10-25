package com.stripe.rainier.compute

import com.stripe.rainier.ir._
import java.io._

class GraphViz {
  private var counter = 0
  private var ids = Map.empty[NonConstant, String]
  private val buf = new StringBuilder
  buf ++= "digraph {\n"

  private def label(value: String): Map[String, String] =
    Map("label" -> value)

  private def struct(sid: String,
                     fields: Seq[(String, Option[String])]): Unit = {
    val fsrcs = 0.to(fields.size).map { i =>
      s"f$i"
    }
    val flabels = fields.map(_._1)
    val label = fsrcs
      .zip(flabels)
      .map {
        case (src, lbl) =>
          s"<$src> $lbl"
      }
      .mkString("|")
    statement(sid, Map("label" -> label, "shape" -> "record"))
    fsrcs.zip(fields.map(_._2)).foreach {
      case (src, Some(dest)) => edge(s"$sid:$src", dest)
      case _                 => ()
    }
  }

  private def opLabel(op: UnaryOp): String =
    op match {
      case ExpOp       => "exp"
      case LogOp       => "log"
      case AbsOp       => "abs"
      case RectifierOp => "relu"
      case NoOp        => "x"
    }

  private def statement(value: String,
                        annotations: Map[String, String]): Unit = {
    buf ++= value
    if (!annotations.isEmpty) {
      buf ++= " ["
      annotations.foreach {
        case (k, v) =>
          buf ++= "\"%s\"=\"%s\"".format(k, v)
      }
      buf ++= "]"
    }
    buf ++= ";\n"
  }

  private def edge(left: String,
                   right: String,
                   annotations: Map[String, String] = Map.empty): Unit =
    statement(s"$left -> $right", annotations)

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
    statement(id, label(value))
    id
  }

  def traverse(r: Real): String =
    r match {
      case Constant(c) => constant("%.2f".format(c))
      case Infinity    => constant("inf")
      case NegInfinity => constant("-inf")
      case nc: NonConstant =>
        val (ncID, seen) = id(nc)
        if (!seen) {
          nc match {
            case Unary(original, op) =>
              val origID = traverse(original)
              struct(ncID, List((opLabel(op), None), ("x", Some(origID))))
            case If(test, nz, z) =>
              val testID = traverse(test)
              val nzID = traverse(nz)
              val zID = traverse(z)
              struct(ncID,
                     List(
                       ("if", None),
                       ("x==0", Some(testID)),
                       ("false", Some(nzID)),
                       ("true", Some(zID))
                     ))
            case Pow(base, exponent) =>
              val baseID = traverse(base)
              val exponentID = traverse(exponent)
              struct(ncID,
                     List(
                       ("^", None),
                       ("", Some(baseID)),
                       ("", Some(exponentID))
                     ))
            case LogLine(ax) =>
              struct(ncID, coefficients("*", "^", ax))
            case l: Line =>
              val coef = coefficients("+", "*", l.ax)
              if (l.b == Real.BigZero)
                struct(ncID, coef)
              else
                struct(ncID, coef :+ ("%0.2f".format(l.b.toDouble) -> None))
            case l: Lookup =>
              val indexID = traverse(l.index)
              val tableIDs = l.table.map(traverse)
              struct(ncID,
                     List(("switch", None), ("x", Some(indexID))) ++
                       tableIDs.zipWithIndex.map {
                         case (t, i) => (i.toString, Some(t))
                       })
            case _: Variable =>
              statement(ncID, label("Variable"))
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
            "x(%d)%s%.2f".format(i, timesOp, a)
        (label, Some(xID))
    }

  def dot = buf.toString + "\n}"
}

object GraphViz {
  def dot(r: Real): String = {
    val g = new GraphViz
    g.traverse(r)
    g.dot
  }

  def write(r: Real, path: String): Unit = {
    val pw = new PrintWriter(new File(path))
    pw.write(dot(r))
    pw.close
  }
}
