package com.stripe.rainier.ir
import java.io._

class GraphViz {
  private val buf = new StringBuilder
  buf ++= "digraph {\n"

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
}
