package com.stripe.rainier.ir
import java.io._

class GraphViz {
  private val buf = new StringBuilder
  buf ++= "digraph {\nsplines=\"false\";\n"

  def record(sid: String, fields: Seq[(String, Option[String])]): Unit = {
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

  def matrix(mid: String,
             label: String,
             columns: Seq[Seq[String]]): Seq[String] = {
    val cdsts = 0.to(columns.size).map { i =>
      s"c$i"
    }
    val colLabel =
      columns
        .zip(cdsts)
        .map {
          case (col, dst) =>
            val values = col.mkString("|")
            if (col.size == 1)
              s"<$dst> $values"
            else
              s"{<$dst> $values}"
        }
        .mkString("|")
    val fullLabel =
      s"$label | $colLabel"
    statement(mid, Map("label" -> fullLabel, "shape" -> "Mrecord"))
    cdsts.map { dst =>
      s"$mid:$dst"
    }
  }

  def statement(value: String, annotations: Map[String, String]): Unit = {
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

  def edge(left: String,
           right: String,
           annotations: Map[String, String] = Map.empty): Unit =
    statement(s"$left -> $right", annotations)

  def dot = buf.toString + "\n}"
  def write(path: String): Unit = {
    val pw = new PrintWriter(new File(path))
    pw.write(dot)
    pw.close
  }

  def subgraph[T](id: String, annotations: Map[String, String])(fn: => T): T = {
    buf ++= "subgraph %s {\n".format(id)
    annotations.foreach {
      case (k, v) =>
        buf ++= "%s=\"%s\";\n".format(k, v)
    }
    val t = fn
    buf ++= "}\n"
    t
  }
}
