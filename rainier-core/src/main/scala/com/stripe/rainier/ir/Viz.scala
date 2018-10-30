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

  private def label(value: String, shape: String): String = {
    val id = nextID()
    gv.statement(id, Map("label" -> value, "shape" -> shape))
    id
  }

  def double(c: Double): String =
    "%.2f".format(c)

  def outputMethod(name: String, methodID: String): Unit = {
    val nameID = label(name, "house")
    gv.edge(nameID, methodID)
  }

  def traverse(r: Expr): String =
    r match {
      case Const(c)     => label(double(c), "oval")
      case _: Parameter => label("θ", "diamond")
      case VarRef(sym)  => label(sym.id.toString, "square")
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
        val id = label(opLabel(op), "oval")
        gv.edge(id, leftID)
        gv.edge(id, rightID)
        id
      case UnaryIR(original, NoOp) =>
        traverse(original)
      case UnaryIR(original, op) =>
        val origID = traverse(original)
        val id = label(opLabel(op), "oval")
        gv.edge(id, origID)
        id
      case IfIR(test, nz, z) =>
        val testID = traverse(test)
        val nzID = traverse(nz)
        val zID = traverse(z)
        val id = nextID()
        gv.record(id,
                  List(
                    ("If", None),
                    ("test", Some(testID)),
                    ("nonZero", Some(nzID)),
                    ("zero", Some(zID))
                  ))
        id
      case LookupIR(index, table) =>
        val indexID = traverse(index)
        val id = nextID()
        gv.record(id,
                  List(("Lookup", None), ("idx", Some(indexID))) ++
                    table.zipWithIndex.map {
                      case (ref, i) =>
                        (i.toString, Some(traverse(ref)))
                    })
        id
      case SeqIR(first, second) =>
        val firstID = traverse(first)
        val secondID = traverse(second)
        val id = label("", "rarrow")
        gv.edge(id, firstID)
        gv.edge(id, secondID, Map("style" -> "bold"))
        id

      case MethodRef(sym) => label(sym.id.toString, "parallelogram")
    }
}

object Viz {
  def apply(exprs: Seq[(String, Expr)], methodSizeLimit: Option[Int]): Viz = {
    val methodGroups = exprs.map {
      case (name, expr) =>
        methodSizeLimit match {
          case Some(l) =>
            val packer = new Packer(l)
            val outputRef = packer.pack(expr)
            (name, outputRef, packer.methods)
          case None =>
            val sym = Sym.freshSym()
            val methods = List(new MethodDef(sym, UnaryIR(expr, NoOp)))
            val outputRef = MethodRef(sym)
            (name, outputRef, methods)
        }
    }
    val allMeths = methodGroups.flatMap(_._3)
    val varTypes = VarTypes.methods(allMeths.toList)
    val viz = new Viz(varTypes)
    val methMap = allMeths.map { methDef =>
      methDef.sym -> viz.traverseSubgraph(methDef.sym.id, "method", methDef.rhs)
    }.toMap
    methodGroups.foreach {
      case (name, outputRef, _) =>
        viz.outputMethod(name, methMap(outputRef.sym))
    }
    viz
  }
}
