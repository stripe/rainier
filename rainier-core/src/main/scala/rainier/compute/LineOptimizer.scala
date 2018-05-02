package rainier.compute

private object LineOptimizer {

  def apply(line: Line): Real = ???
/*
	+
-    case Constant(v)     => new Line(ax, b + v)	
-    case l: Line         => new Line(merge(ax, l.ax), l.b + b)	
-    case nc: NonConstant => new Line(ax + (nc -> 1.0), b)	
-  }	
-  override def *(other: Real): Real = other match {	
-    case Constant(v) => new Line(ax.map { case (r, d) => r -> d * v }, b * v)
*/

  def factor(line: Line): Option[(NonConstant, Double)] = ???
  
  def log(line: Line): Option[Real] =
    factor(line).map{case (y,k) =>
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

  private def multiply1D(a: Double, x: NonConstant, b: Double, c: Double, y: NonConstant, d: Double): Option[Real] = ???

}