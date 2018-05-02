package rainier.compute

private object LineOptimizer {

  def apply(line: Line): Real = ???

/*
  def +(other: Real) = other match {
    case Constant(v)   => Line(Map(this -> 1.0), v)
    case nc: NonConstant =>
      Line(Map(this -> 1.0, nc -> 1.0), 0.0)
  }

  def *(other: Real): Real = other match {
    case Constant(v)     => Line(Map(this -> v), 0.0)
  }


	+
-    case Constant(v)     => new Line(ax, b + v)	
-    case l: Line         => new Line(merge(ax, l.ax), l.b + b)	
-    case nc: NonConstant => new Line(ax + (nc -> 1.0), b)	
-  }	
-  override def *(other: Real): Real = other match {	
-    case Constant(v) => new Line(ax.map { case (r, d) => r -> d * v }, b * v)
*/

  def log(line: Line): Option[Real] =
    factor(line)
      .filter(_._2 >= 0)
      .map{case (y,k) =>
        y.log + Math.log(k)
      }

  def pow(line: Line, exponent: Real): Option[Real] =
    exponent match {
      case Constant(p) =>
        factor(line).map{case (y,k) =>
          y.pow(p) * Math.pow(k,p)
        }
      case _ => None
    }

  def sum(left: Line, right: Line): Real = 
    Line(merge(left.ax, right.ax), left.b + right.b)

  def multiply(left: Line, right: Line): Option[Real] =
    if(left.ax.size == 1 && right.ax.size == 1)
      multiply1D(
        left.ax.head._2,
        left.ax.head._1,
        left.b,
        right.ax.head._2,
        right.ax.head._1,
        right.b)
    else
      None

  //if the result is Some((y,k)), then y*k==line, k != 1
  def factor(line: Line): Option[(NonConstant, Double)] =
    if (line.ax.size == 1 && line.b == 0) {
      val a = line.ax.head._2
      val x = line.ax.head._1
      if(a == 1.0)
        None
      else
        Some((x, a))
    }	else {	
      val mostSimplifyingFactor =	
        line.ax.values	
          .groupBy(_.abs)	
          .map { case (d, l) => d -> l.size }	
          .toList	
          .sortBy(_._2)	
          .last	
          ._1	

      if(mostSimplifyingFactor == 1.0)
        None
      else {
        val newAx = line.ax.map {	
          case (r, d) => r -> d / mostSimplifyingFactor	
        }	
	
        val newB = b / mostSimplifyingFactor	
	
        Some((Line(newAx, newB), mostSimplifyingFactor))	
      }
    }	
  }	

  private def multiply1D(a: Double, x: NonConstant, b: Double, c: Double, y: NonConstant, d: Double): Option[Real] = ???


  private def merge(	
      left: Map[NonConstant, Double],	
      right: Map[NonConstant, Double]): Map[NonConstant, Double] = {	
    val (big, small) =	
      if (left.size > right.size)	
        (left, right)	
      else	
        (right, left)	
	
    small.foldLeft(big) {	
      case (acc, (k, v)) =>	
        val newV = big	
          .get(k)	
          .map { bigV =>	
            bigV + v	
          }	
          .getOrElse(v)	

        if (newV == 0.0)	
          acc - k	
        else	
          acc + (k -> newV)	
    }	
  }
}