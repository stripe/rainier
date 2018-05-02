package rainier.compute

object Optimizer {
  def apply(unary: Unary): Real = unary
  def apply(product: Product): Real = product
  def apply(pow: Pow): Real = pow
  def apply(line: Line): Real = line
}
