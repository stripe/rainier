package com.stripe.rainier.ir

class Viz(varTypes: VarTypes) {
  val gv = new GraphViz

  private var counter = 0
  def nextID(): String = {
    counter += 1
    s"r$counter"
  }

  private def opLabel(op: UnaryOp): String =
    op match {
      case ExpOp       => "exp"
      case LogOp       => "log"
      case AbsOp       => "abs"
      case RectifierOp => "relu"
      case NoOp        => "x"
    }

  private def opLabel(op: BinaryOp): String =
    op match {
      case AddOp      => "+"
      case MultiplyOp => "*"
      case SubtractOp => "-"
      case DivideOp   => "/"
      case PowOp      => "^"
    }

  private def label(value: String): String = {
    val id = nextID()
    gv.statement(id, Map("label" -> value))
    id
  }

  def double(c: Double): String =
    "%.2f".format(c)

  def traverse(r: Expr): String =
    r match {
      case Const(c)     => label(double(c))
      case _: Parameter => label("θ")
      case VarRef(sym)  => label(sym.id.toString)
      case VarDef(sym, rhs) =>
        traverseDef(sym, rhs)
    }

  def traverseDef(sym: Sym, ir: IR): String =
    varTypes(sym) match {
      case Inline =>
        traverseIR(ir)
      case Local(_) =>
        traverseSubgraph(sym.id, "local", ir)
      case Global(_) =>
        traverseSubgraph(sym.id, "global", ir)
    }

  def traverseSubgraph(id: Int, scope: String, ir: IR): String =
    gv.subgraph(s"cluster_$id", s"$id ($scope)") {
      traverseIR(ir)
    }

  def traverseIR(ir: IR): String =
    ir match {
      case BinaryIR(left, right, op) =>
        val leftID = traverse(left)
        val rightID = traverse(right)
        val id = nextID()
        gv.record(id,
                  List(
                    (opLabel(op), None),
                    ("", Some(leftID)),
                    ("", Some(rightID))
                  ))
        id
      case UnaryIR(original, NoOp) =>
        traverse(original)
      case UnaryIR(original, op) =>
        val origID = traverse(original)
        val id = nextID()
        gv.record(id,
                  List(
                    (opLabel(op), None),
                    ("", Some(origID))
                  ))
        id
      case IfIR(test, nz, z) =>
        val testID = traverse(test)
        val nzID = traverse(nz)
        val zID = traverse(z)
        val id = nextID()
        gv.record(id,
                  List(
                    ("if", None),
                    ("x==0", Some(testID)),
                    ("false", Some(nzID)),
                    ("true", Some(zID))
                  ))
        id
      case LookupIR(index, table) =>
        val indexID = traverse(index)
        val id = nextID()
        gv.record(id,
                  List(("switch", None), ("", Some(indexID))) ++
                    table.zipWithIndex.map {
                      case (ref, i) =>
                        (i.toString, Some(traverse(ref)))
                    })
        id
      case SeqIR(first, second) =>
        val firstID = traverse(first)
        val secondID = traverse(second)
        val id = nextID()
        gv.record(id,
                  List(
                    ("first", Some(firstID)),
                    ("next", Some(secondID))
                  ))
        id

      case MethodRef(sym) => label(sym.id.toString)
    }
}

object Viz {
  def apply(exprs: Seq[(String, Expr)]): Viz = {
    val methodGroups = exprs.map {
      case (name, expr) =>
        val packer = new Packer(200)
        val outputRef = packer.pack(expr)
        (name, outputRef, packer.methods)
    }
    val allMeths = methodGroups.flatMap(_._3)
    val varTypes = VarTypes.methods(allMeths.toList)
    val viz = new Viz(varTypes)
    allMeths.foreach { methDef =>
      viz.traverseSubgraph(methDef.sym.id, "method", methDef.rhs)
    }
    viz
  }
}
