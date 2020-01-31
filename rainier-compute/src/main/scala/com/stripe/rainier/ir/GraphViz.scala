package com.stripe.rainier.ir
import java.io._

class GraphViz {
  import GraphViz._

  private val buf = new StringBuilder
  buf ++= "digraph {\n rankdir=LR;\n"

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

  def binaryRecord(op: String,
                   left: Either[String, String],
                   right: Either[String, String]): String = {

    val labels =
      List(left.right.getOrElse(""), op, right.right.getOrElse(""))
    val (id, slotIDs) = record(labels)
    left.left.foreach { leftID =>
      edge(slotIDs(0), leftID)
    }
    right.left.foreach { rightID =>
      edge(slotIDs(2), rightID)
    }
    id
  }

  def record(labels: Seq[String],
             attrs: (String, String)*): (String, Seq[String]) =
    record(false, labels, attrs: _*)

  def record(isVertical: Boolean,
             labels: Seq[String],
             attrs: (String, String)*): (String, Seq[String]) = {
    val ports = 1.to(labels.size).map { i =>
      s"f$i"
    }
    val cells =
      labels.zip(ports).map { case (l, p) => s"<$p> $l" }.mkString("|")
    val fullLabel =
      if (isVertical)
        s"{$cells}"
      else
        cells
    val id = node((label(fullLabel) :: shape("record") :: attrs.toList): _*)
    (id, ports.map { p =>
      s"$id:$p"
    })
  }

  def line(size: Int, clr: String, attrs: (String, String)*): Seq[String] = {
    val ids = List.fill(size) {
      node((shape("point") :: attrs.toList): _*)
    }
    rank("sink", ids)
    ids.sliding(2, 1).foreach {
      case List(a, b) => edge(a, b, color(clr), "arrowhead" -> "none")
      case _          => ()
    }
    ids
  }

  def edge(dest: String, src: String, attrs: (String, String)*): Unit = {
    buf ++= s"$src -> $dest"
    attributes(attrs)
    buf ++= ";\n"
  }

  def rank(level: String, ids: Seq[String]): Unit = {
    buf ++= s"{rank = $level; "
    ids.foreach { id =>
      buf ++= id
      buf ++= ";"
    }
    buf ++= "};\n"
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

  def dot: String = buf.toString + "\n}"
  def write(path: String): Unit = {
    val pw = new PrintWriter(new File(path))
    pw.write(dot)
    pw.close
  }
}

object GraphViz {
  def label(v: String): (String, String) =
    "label" -> v
  def xlabel(v: String): (String, String) =
    "xlabel" -> v
  def color(v: String): (String, String) =
    "color" -> v
  def shape(v: String): (String, String) =
    "shape" -> v
  def style(v: String): (String, String) =
    "style" -> v
  def justify(v: String): (String, String) =
    "labeljust" -> v
  def location(v: String): (String, String) =
    "labelloc" -> v

  def formatDouble(d: Double): String = {
    if (d.isNegInfinity)
      "-∞"
    else if (d.isInfinity)
      "∞"
    else {
      val eps = Math.abs(d - Math.round(d))
      if (eps > 0.01)
        "%.2f".format(d)
      else
        d.toInt.toString
    }
  }
}
