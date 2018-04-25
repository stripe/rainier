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
  enablePlugins(TutPlugin).
  settings(
    libraryDependencies += "org.ow2.asm" % "asm" % "6.0",
    // https://mvnrepository.com/artifact/org.ow2.asm/asm-tree
    libraryDependencies += "org.ow2.asm" % "asm-tree" % "6.0",
    // https://mvnrepository.com/artifact/commons-io/commons-io
    libraryDependencies += "commons-io" % "commons-io" % "2.6"
  )

lazy val rainierExample = project.
  in(file("rainier-example")).
  dependsOn(rainierCore).
  settings(unpublished: _*)

lazy val rainierBenchmark = project.
  in(file("rainier-benchmark")).
  dependsOn(rainierCore).
  settings(unpublished: _*)
