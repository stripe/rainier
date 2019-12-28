package com.stripe.rainier.compute

import com.stripe.rainier.ir._

private[compute] object RealOps {
  private val Infinity = Real.infinity
  private val NegInfinity = Real.negInfinity
  private val Zero = Real.zero
  private val One = Real.one

  def unary(original: Real, op: UnaryOp): Real =
    original match {
      case Scalar(value) =>
        Scalar(DecimalOps.unary(value, op))
      case c: Column =>
        c.map{x => DecimalOps.unary(x, op)}
      case nc: NonConstant =>
        val opt = (op, nc) match {
          case (ExpOp, Unary(x, LogOp))     => Some(x)
          case (AbsOp, u @ Unary(_, AbsOp)) => Some(u)
          case (AbsOp, u @ Unary(_, ExpOp)) => Some(u)
          case (LogOp, Unary(x, ExpOp))     => Some(x)
          case (LogOp, l: Line)             => LineOps.log(l)
          case (LogOp, l: LogLine)          => LogLineOps.log(l)
          case _                            => None
        }
        opt.getOrElse(Unary(nc, op))
    }

  def add(left: Real, right: Real): Real =
    (left, right) match {
      case (x: Constant, y: Constant)   => addC(x, y)
      case (Infinity, _)                => left
      case (_, Infinity)                => right
      case (NegInfinity, _)             => left
      case (_, NegInfinity)             => right
      case (_, Zero)                    => left
      case (Zero, _)                    => right
      case (c: Constant, nc: NonConstant) => LineOps.translate(nc, c)
      case (nc: NonConstant, c: Constant) => LineOps.translate(nc, c)
      case (nc1: NonConstant, nc2: NonConstant) =>
        LineOps.sum(nc1, nc2)
    }

  def addC(left: Constant, right: Constant): Constant = 
    (left, right) match {
      case (Scalar(x), Scalar(y))       =>
        Scalar(DecimalOps.add(x, y))
      case (Scalar(x), ys: Column) =>
        ys.map{y => DecimalOps.add(x, y)}
      case (xs: Column, Scalar(y)) =>
        xs.map{x => DecimalOps.add(x, y)}
      case (xs: Column, ys: Column) =>
        xs.zipMap(ys){(x,y) => DecimalOps.add(x, y)}
    }

  def multiply(left: Real, right: Real): Real =
    (left, right) match {
      case (x: Constant, y: Constant)       => multiplyC(x, y)
      case (Infinity, r)                => Real.gt(r, 0, Infinity, NegInfinity)
      case (r, Infinity)                => Real.gt(r, 0, Infinity, NegInfinity)
      case (NegInfinity, r)             => Real.gt(r, Real.zero, NegInfinity, Infinity)
      case (r, NegInfinity)             => Real.gt(r, Real.zero, NegInfinity, Infinity)
      case (_, Zero)                    => Real.zero
      case (Zero, _)                    => Real.zero
      case (_, One)                     => left
      case (One, _)                     => right
      case (c: Constant, nc: NonConstant) => LineOps.scale(nc, c)
      case (nc: NonConstant, c: Constant) => LineOps.scale(nc, c)
      case (nc1: NonConstant, nc2: NonConstant) =>
        LogLineOps.multiply(LogLine(nc1), LogLine(nc2))
    }

  def multiplyC(left: Constant, right: Constant): Constant = ???

  def divide(left: Real, right: Real): Real =
    (left, right) match {
      case (Scalar(x), Scalar(y)) => Scalar(DecimalOps.divide(x, y))
      case (_, Zero)              => left * Infinity
      case _                      => left * right.pow(-1)
    }

  def min(left: Real, right: Real): Real =
    Real.lt(left, right, left, right)

  def max(left: Real, right: Real): Real =
    Real.gt(left, right, left, right)

  def pow(original: Real, exponent: Real): Real =
    exponent match {
      case Scalar(e)      => pow(original, e)
      case e: NonConstant => Pow(original, e)
    }

  def pow(original: Real, exponent: Decimal): Real =
    (original, exponent) match {
      case (Scalar(v), _)    => Real(DecimalOps.pow(v, exponent))
      case (_, Infinity)     => Infinity
      case (_, NegInfinity)  => Zero
      case (_, Decimal.Zero) => One
      case (_, Decimal.One)  => original
      case (l: Line, _) =>
        LineOps.pow(l, exponent).getOrElse {
          LogLineOps.pow(LogLine(l), exponent)
        }
      case (nc: NonConstant, _) =>
        LogLineOps.pow(LogLine(nc), exponent)
    }

  def compare(left: Real, right: Real): Real =
    (left, right) match {
      case (Scalar(a), Scalar(b)) =>
        Scalar(DecimalOps.compare(a, b))
      case (Infinity, _)    => One
      case (_, Infinity)    => Scalar(Decimal(-1))
      case (NegInfinity, _) => Scalar(Decimal(-1))
      case (_, NegInfinity) => One
      case _                => Compare(left, right)
    }

  def parameters(real: Real): Set[Parameter] =
    leaves(real).collect { case Left(p) => p }
  def columns(real: Real): Set[Column] =
    leaves(real).collect { case Right(c) => c }

  private def leaves(real: Real): Set[Either[Parameter, Column]] = {
    var seen = Set.empty[Real]
    var leaves = List.empty[Either[Parameter, Column]]
    def loop(r: Real): Unit =
      if (!seen.contains(r)) {
        seen += r
        r match {
          case Scalar(_) => ()
          case v: Column => leaves = Right(v) :: leaves
          case v: Parameter =>
            leaves = Left(v) :: leaves
            loop(v.density)
          case u: Unary   => loop(u.original)
          case l: Line    => l.ax.terms.foreach(loop)
          case l: LogLine => l.ax.terms.foreach(loop)
          case Compare(left, right) =>
            loop(left)
            loop(right)
          case Pow(base, exponent) =>
            loop(base)
            loop(exponent)
          case l: Lookup =>
            loop(l.index)
            l.table.foreach(loop)
        }
      }

    loop(real)

    leaves.toSet
  }

  //see whether we can reduce this from a function on a matrix of
  //placeholder data (O(N) to compute, where N is the rows in the matrix)
  //to an O(1) function just on the parameters; this should be possible if the function can be expressed
  //as a linear combination of terms that are each functions of either a placeholder,
  //or a parameter, but not both.
  def inlinable(real: Real): Boolean = {
    case class State(hasParameter: Boolean,
                     hasPlaceholder: Boolean,
                     nonlinearCombination: Boolean) {
      def ||(other: State) = State(
        hasParameter || other.hasParameter,
        hasPlaceholder || other.hasPlaceholder,
        nonlinearCombination || other.nonlinearCombination
      )

      def combination = hasParameter && hasPlaceholder

      def nonlinearOp =
        State(hasParameter, hasPlaceholder, combination)

      def inlinable = !nonlinearCombination
    }

    var seen = Map.empty[Real, State]

    def loopMerge(rs: Seq[Real]): State =
      rs.map(loop).reduce(_ || _)

    def loop(r: Real): State =
      if (!seen.contains(r)) {
        val result = r match {
          case Scalar(_) =>
            State(false, false, false)
          case _: Column =>
            State(false, true, false)
          case _: Parameter =>
            State(true, false, false)
          case u: Unary =>
            loopMerge(List(u.original)).nonlinearOp
          case l: Line =>
            loopMerge(l.ax.terms.toList)
          case l: LogLine =>
            val terms = l.ax.terms.toList
            val state = loopMerge(terms)
            if (state.nonlinearCombination || !state.combination)
              state
            else {
              val termStates = terms.map(loop)
              if (termStates.exists(_.combination))
                state.nonlinearOp
              else
                state
            }
          case Compare(left, right) =>
            loopMerge(List(left, right)).nonlinearOp
          case Pow(base, exponent) =>
            loopMerge(List(base, exponent)).nonlinearOp
          case l: Lookup =>
            val tableState = loopMerge(l.table.toList)
            val indexState = loop(l.index)
            val state = tableState || indexState

            if (indexState.hasParameter)
              state.nonlinearOp
            else
              state
        }
        seen += (r -> result)
        result
      } else {
        seen(r)
      }

    //real+real will trigger a distribute() if warranted
    loop(real + real).inlinable
  }
}
