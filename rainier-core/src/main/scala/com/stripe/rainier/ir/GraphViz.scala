package com.stripe.rainier.ir
import java.io._

class GraphViz {
  private val buf = new StringBuilder
  buf ++= "digraph {\nsplines=\"ortho\";\n"

  private var counter = 0
  private def nextID() = {
    counter += 1
    s"r$counter"
  }

  def node(attrs: (String, String)*): String = {
    val id = nextID()
    buf ++= id
    attributes(attrs)
    buf ++= ";\n"
    id
  }

  def edge(left: String, right: String, attrs: (String, String)*): Unit = {
    buf ++= s"$left -> $right"
    attributes(attrs)
    buf ++= ";\n"
  }

  def cluster[T](attrs: (String, String)*)(fn: => T): T = {
    val id = nextID()
    buf ++= "subgraph cluster_%s {\n".format(id)
    attrs.foreach {
      case (k, v) =>
        buf ++= "%s=\"%s\";\n".format(k, v)
    }
    val t = fn
    buf ++= "}\n"
    t
  }

  private def attributes(seq: Seq[(String, String)]): Unit = {
    if (!seq.isEmpty) {
      buf ++= " ["
      seq.foreach {
        case (k, v) =>
          buf ++= "\"%s\"=\"%s\"".format(k, v)
      }
      buf ++= "]"
    }
  }

  def dot = buf.toString + "\n}"
  def write(path: String): Unit = {
    val pw = new PrintWriter(new File(path))
    pw.write(dot)
    pw.close
  }
}

object GraphViz {
  def label(v: String): (String, String) =
    "label" -> v
  def color(v: String): (String, String) =
    "color" -> v
  def shape(v: String): (String, String) =
    "shape" -> v
  def style(v: String): (String, String) =
    "style" -> v
  def justify(v: String): (String, String) =
    "labeljust" -> v

  def formatDouble(d: Double): String =
    "%.2f".format(d)
}
