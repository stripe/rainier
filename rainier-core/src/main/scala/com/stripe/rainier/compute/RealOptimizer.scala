package com.stripe.rainier.compute

import com.stripe.rainier.ir
import com.stripe.dagon._

object RealOptimizer {
  // Reals are untyped as far as Dagon goes
  type RealN[A] = Real
  type RealLit[A] = Literal[RealN, A]

  /**
    * Dagon requires mapping your DAG nodes onto a unified
    * AST to allow it to see the structure of your DAG
    */
  val toLiteral: FunctionK[RealN, RealLit] =
    Memoize.functionK(new Memoize.RecursiveK[RealN, RealLit] {

      def lens(c: Coefficients): (List[Real], List[Real] => Coefficients) =
        c match {
          case Coefficients.EmptyCoeff =>
            (Nil, _ => Coefficients.EmptyCoeff)
          case Coefficients.One(term, co) =>
            (term :: Nil, {
              case (h: NonConstant) :: Nil => Coefficients.One(h, co)
              case other =>
                sys.error(
                  s"invalid transformation: $c must only be converted to One, found: $other")
            })
          case m @ Coefficients.Many(_, _) =>
            val mList = m.toList
            val decList = mList.map(_._2)
            (mList.map(_._1), {
              (rs: List[Real]) =>
                val rsize = rs.size
                require(rsize == m.size,
                        s"expected size: ${m.size} but found: $rsize: $rs")
                val terms = rs.map {
                  case nc: NonConstant => nc
                  case other =>
                    sys.error(
                      s"found non-constant in parameters: $other from $m")
                }

                Coefficients.Many(terms.zip(decList).toMap, terms)
            })

        }

      def toFunction[A] = {
        case (c @ (Constant(_) | Infinity | NegInfinity), _) =>
          Literal.Const[RealN, A](c)
        case (v: Variable, _) =>
          Literal.Const[RealN, A](v)
        case (Compare(a, b), rec) =>
          Literal.Binary[RealN, A, A, A](rec(a), rec(b), Compare(_, _))
        case (l: Line, rec) =>
          val (e, d) = lens(l.ax)
          Literal.Variadic[RealN, A, A](e.map(rec[A](_)), { rs: List[Real] =>
            Line(d(rs), l.b)
          })
        case (LogLine(c), rec) =>
          val (e, d) = lens(c)
          Literal.Variadic[RealN, A, A](e.map(rec[A](_)), { rs: List[Real] =>
            LogLine(d(rs))
          })
        case (l: Lookup, rec) =>
          val argsAsList: List[Real] = l.index :: l.table.toList
          Literal.Variadic[RealN, A, A](argsAsList.map(rec[A](_)), {
            rs: List[Real] =>
              val h :: t = rs
              Lookup(h, t, l.low)
          })
        case (Pow(b, e), rec) =>
          Literal.Binary[RealN, A, A, A](
            rec(b),
            rec(e),
            // This cast is safe since the rewrite rules
            // operate on Real, which will require this
            { (b: Real, exp: Real) =>
              Pow(b, exp.asInstanceOf[NonConstant])
            }
          )
        case (Unary(v, op), rec) =>
          // This cast is safe since the rewrite rules
          // operate on Real, which will require this
          Literal.Unary[RealN, A, A](rec(v), { e: Real =>
            Unary(e.asInstanceOf[NonConstant], op)
          })
      }
    })

  /**
    * Apply the given rule to a Real
    */
  def apply(r: Real, rule: Rule[RealN]): Real = {
    val (dag, id) = Dag[Any, RealN](r, toLiteral)

    val optDag = dag(rule)
    optDag.evaluate(id)
  }

  /**
    * Run all the optimizations on this formula
    */
  def apply(r: Real): Real =
    apply(r, allRules)

  case object ExpLogInverse extends Rule[RealN] {
    def apply[A](dag: Dag[RealN]) = {
      case Unary(inner @ Unary(v, ir.LogOp), ir.ExpOp)
          if dag.hasSingleDependent(inner) =>
        // exp(log(x)) = x, and we should remove it if no one else needs inner
        Some(v)
      case Unary(inner @ Unary(v, ir.ExpOp), ir.LogOp)
          if dag.hasSingleDependent(inner) =>
        // exp(log(x)) = x, and we should remove it if no one else needs v
        Some(v)
      case _ => None
    }
  }

  case object RedundantAbs extends Rule[RealN] {
    def apply[A](dag: Dag[RealN]) = {
      case Unary(inner @ Unary(_, ir.ExpOp), ir.AbsOp) =>
        // exp(n) is already positive
        Some(inner)
      case Unary(inner @ Unary(_, ir.AbsOp), ir.AbsOp) =>
        // inner is already positive
        Some(inner)
      case _ => None
    }
  }

  val allRules: Rule[RealN] = RedundantAbs.orElse(ExpLogInverse)
}
