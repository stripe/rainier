organization in ThisBuild := "com.stripe"
scalaVersion in ThisBuild := "2.12.3"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-optimize"
)

val unpublished = Seq(publish := (), publishLocal := (), publishArtifact := false)

scalafmtOnCompile in ThisBuild := true

lazy val root = project.
  in(file(".")).
  dependsOn(rainierCore).
  aggregate(rainierCore, rainierExample, rainierBenchmark).
  settings(unpublished: _*)

lazy val rainierCore = project.
  in(file("rainier-core")).
  enablePlugins(TutPlugin)

lazy val rainierExample = project.
  in(file("rainier-example")).
  dependsOn(rainierCore).
  settings(unpublished: _*)

lazy val rainierBenchmark = project.
  in(file("rainier-benchmark")).
  dependsOn(rainierCore).
  settings(unpublished: _*)
