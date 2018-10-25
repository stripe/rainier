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

  private def opLabel(op: UnaryOp): String =
    op match {
      case ExpOp       => "exp"
      case LogOp       => "log"
      case AbsOp       => "abs"
      case RectifierOp => "relu"
      case NoOp        => ""
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
              statement(ncID, label(opLabel(op)))
              val origID = traverse(original)
              edge(ncID, origID)
            case If(test, nz, z) =>
              statement(ncID, Map("shape" -> "diamond", "label" -> "if(x==0)"))
              val testID = traverse(test)
              val nzID = traverse(nz)
              val zID = traverse(z)
              edge(ncID, testID, label("x"))
              edge(ncID, nzID, label("false"))
              edge(ncID, zID, label("true"))
            case Pow(base, exponent) =>
              statement(ncID, label("x^y"))
              val baseID = traverse(base)
              val exponentID = traverse(exponent)
              edge(ncID, baseID, label("x"))
              edge(ncID, exponentID, label("y"))
            case LogLine(ax) =>
              statement(ncID, label("π(x^a)"))
              coefficients(ncID, ax)
            case l: Line =>
              if (l.b == Real.BigZero)
                statement(ncID, label("x•a"))
              else
                statement(ncID, label("x•a + %.2f".format(l.b.toDouble)))
              coefficients(ncID, l.ax)
            case l: Lookup =>
              statement(ncID, label("table(index)"))
              val indexID = traverse(l.index)
              val tableIDs = l.table.map(traverse)
              edge(ncID, indexID, label("index"))
              tableIDs.zipWithIndex.map {
                case (tID, t) =>
                  edge(ncID, tID, label(s"table($t)"))
              }
            case _: Variable =>
              statement(ncID, Map("label" -> "Variable"))
          }
        }
        ncID
    }

  private def coefficients(ncID: String, ax: Coefficients): Unit =
    ax.toList.foreach {
      case (x, a) =>
        val xID = traverse(x)
        val annotations =
          if (a == 1.0)
            Map.empty[String, String]
          else
            label("%.2f".format(a))
        edge(ncID, xID, annotations)
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
