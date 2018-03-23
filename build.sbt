import Dependencies._

enablePlugins(TutPlugin)

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.stripe",
      scalaVersion := "2.12.3",
      version      := "0.1.0-SNAPSHOT",
      scalafmtOnCompile := true
    )),
    name := "rainier",
    libraryDependencies += scalaTest % Test
  )
