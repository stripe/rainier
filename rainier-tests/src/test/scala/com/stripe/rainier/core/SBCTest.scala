package com.stripe.rainier.core

//import com.stripe.rainier.compute._
//import com.stripe.rainier.sampler._
import org.scalatest.FunSuite

/** This is what happens when we make the test extend App
 * [warn] Multiple main classes detected.  Run 'show discoveredMainClasses' to see the list
 * [error] java.lang.RuntimeException: No main class detected.
 * running discoveredMainClasses returns no main classes detected
 */
object SBCTest extends FunSuite {

  implicit val rng: RNG = ScalaRNG(1528673302081L)
  def check[L, T](goldsetSamples: List[Double], sbcModel: SBCModel[L, T]) = {
    test(sbcModel.description) {
      assert(sbcModel.samples(goldsetSamples.size) == goldsetSamples)
    }
  }
}
