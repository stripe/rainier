package com.stripe.rainier.ir

import scala.util.control.TailCalls
import TailCalls.TailRec

private class Packer(methodSizeLimit: Int) {
  private var methodDefs: List[MethodDef] = Nil
  def methods = methodDefs

  def pack(p: Expr): MethodRef = {
    val (pExpr, _) = traverse(p, 0).result
    createMethod(UnaryIR(pExpr, NoOp))
  }

  private def traverse(p: Expr, parentSize: Int): TailRec[(Expr, Int)] =
    p match {
      case v: VarDef => traverseVarDef(v, parentSize)
      case _: Ref    => TailCalls.done((p, 1))
    }

  private def traverseVarDef(v: VarDef,
                             parentSize: Int): TailRec[(VarDef, Int)] =
    TailCalls.tailcall(traverseIR(v.rhs).map {
      case (ir, irSize) =>
        val (newIR, newSize) =
          if ((irSize + parentSize) > methodSizeLimit)
            (createMethod(ir), 1)
          else
            (ir, irSize)
        (new VarDef(v.sym, newIR), newSize + 1)
    })

  private def traverseIR(p: IR): TailRec[(IR, Int)] =
    p match {
      case b: BinaryIR =>
        traverse(b.left, 2).flatMap {
          case (leftExpr, leftSize) =>
            traverse(b.right, leftSize + 1).map {
              case (rightExpr, rightSize) =>
                (new BinaryIR(leftExpr, rightExpr, b.op),
                 leftSize + rightSize + 1)
            }
        }
      case u: UnaryIR =>
        traverse(u.original, 1).map {
          case (expr, exprSize) =>
            (new UnaryIR(expr, u.op), exprSize + 1)
        }
      case l: LookupIR =>
        traverse(l.index, l.table.size).map {
          case (indexExpr, indexSize) =>
            (new LookupIR(indexExpr, l.table, l.low), indexSize + l.table.size)
        }
      case s: SeqIR =>
        traverseVarDef(s.first, 1).flatMap {
          case (firstDef, firstSize) =>
            traverseVarDef(s.second, firstSize + 1).map {
              case (secondDef, secondSize) =>
                (SeqIR(firstDef, secondDef), firstSize + secondSize + 1)
            }
        }
      case _: MethodRef =>
        sys.error("there shouldn't be any method refs yet")
    }

  private def createMethod(rhs: IR): MethodRef = {
    val s = Sym.freshSym()
    val md = new MethodDef(s, rhs)
    methodDefs = md :: methodDefs
    MethodRef(s)
  }
}
