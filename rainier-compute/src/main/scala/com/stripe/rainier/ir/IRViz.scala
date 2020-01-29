package com.stripe.rainier.ir

private class IRViz(parameters: Seq[Param], methodDefs: List[MethodDef]) {
  import GraphViz._
  val gv = new GraphViz

  private val varTypes = VarTypes.methods(methodDefs)
  private var methods = Map.empty[Sym, String]
  private var paramMap =
    parameters.zipWithIndex.map {
      case (p, i) => p -> s"θ_$i"
    }.toMap

  methodDefs.foreach { methDef =>
    methods +=
      (methDef.sym ->
        traverseDef("", "black", methDef.rhs))
  }

  def outputMethod(name: String, sym: Sym): Unit = {
    val id = gv.node(label(name), shape("house"))
    gv.edge(id, methods(sym))
    gv.rank("sink", List(id))
  }

  def idOrLabel(r: Expr): Either[String, String] =
    r match {
      case VarDef(sym, rhs) => Left(traverseVarDef(sym, rhs))
      case ref: Ref         => Right(refLabel(ref))
    }

  def paramLabel(p: Param): String =
    paramMap.get(p) match {
      case Some(s) => s
      case None =>
        val i = paramMap.size - parameters.size
        val s = s"X_$i"
        paramMap += (p -> s)
        s
    }

  def refLabel(r: Ref): String =
    r match {
      case Const(c)    => formatDouble(c)
      case p: Param    => paramLabel(p)
      case VarRef(sym) => varSlot(sym)
    }

  def traverse(r: Expr): String =
    idOrLabel(r) match {
      case Left(id) => id
      case Right(l) =>
        gv.node(
          label(l),
          shape("square")
        )
    }

  def varColor(sym: Sym): String =
    varTypes(sym) match {
      case Inline    => ""
      case Local(_)  => "blue"
      case Global(_) => "red"
    }

  def varSlot(sym: Sym): String =
    varTypes(sym) match {
      case Inline    => ""
      case Local(x)  => s"t$x"
      case Global(x) => s"g$x"
    }

  def traverseVarDef(sym: Sym, ir: IR): String =
    varTypes(sym) match {
      case Inline =>
        traverseIR(ir)
      case _ => traverseDef(varSlot(sym), varColor(sym), ir)
    }

  def traverseDef(slot: String, clr: String, ir: IR): String =
    gv.cluster(label(slot), justify("l"), color(clr)) {
      traverseIR(ir)
    }

  def traverseIR(ir: IR): String =
    ir match {
      case BinaryIR(left, right, op) =>
        gv.binaryRecord(IRViz.opLabel(op), idOrLabel(left), idOrLabel(right))
      case UnaryIR(original, NoOp) =>
        traverse(original)
      case UnaryIR(original, op) =>
        val origID = traverse(original)
        val id = gv.node(label(IRViz.opLabel(op)), shape("oval"))
        gv.edge(id, origID)
        id
      case LookupIR(index, table, _) =>
        val refLabels = table.map(refLabel)
        val (id, slotIDs) = gv.record("⋲" :: refLabels.toList)
        val indexID = traverse(index)
        gv.edge(slotIDs.head, indexID)
        id
      case SeqIR(first, second) =>
        val firstID = traverseVarDef(first.sym, first.rhs)
        val secondID = traverseVarDef(second.sym, second.rhs)
        val id = gv.node(label(""), shape("rarrow"))
        gv.edge(id, firstID)
        gv.edge(id, secondID, style("bold"))
        id
      case MethodRef(sym) => methods(sym)
    }
}

object IRViz {
  def apply(exprs: Seq[(String, Expr)],
            parameters: Seq[Param],
            methodSizeLimit: Option[Int]): GraphViz = {
    val methodGroups = exprs.toList.map {
      case (name, expr) =>
        methodSizeLimit match {
          case Some(l) =>
            val packer = new Packer(l)
            val outputRef = packer.pack(expr)
            (name, outputRef, packer.methods.reverse)
          case None =>
            val sym = Sym.freshSym()
            val methods = List(new MethodDef(sym, UnaryIR(expr, NoOp)))
            val outputRef = MethodRef(sym)
            (name, outputRef, methods)
        }
    }
    val allMeths = methodGroups.flatMap(_._3)
    val viz = new IRViz(parameters, allMeths)
    methodGroups.foreach {
      case (name, outputRef, _) =>
        viz.outputMethod(name, outputRef.sym)
    }
    viz.gv
  }

  def opLabel(op: UnaryOp): String =
    op match {
      case ExpOp  => "exp"
      case LogOp  => "ln"
      case AbsOp  => "abs"
      case SinOp  => "sin"
      case CosOp  => "cos"
      case TanOp  => "tan"
      case AsinOp => "asin"
      case AcosOp => "acos"
      case AtanOp => "atan"
      case NoOp   => "nop"
    }

  def opLabel(op: BinaryOp): String =
    op match {
      case AddOp      => "+"
      case MultiplyOp => "*"
      case SubtractOp => "-"
      case DivideOp   => "/"
      case PowOp      => "^"
      case CompareOp  => "⟺"
    }
}
