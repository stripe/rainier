import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3"
  lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
}
