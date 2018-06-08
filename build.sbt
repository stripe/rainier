lazy val root = project.
  in(file(".")).
  aggregate(rainierCore, rainierPlot, rainierCats, rainierScalacheck).
  aggregate(rainierDocs, rainierExample).
  aggregate(rainierBenchmark, rainierTests).
  aggregate(shadedAsm).
  settings(unpublished)

/* dependency versions */
lazy val V = new {
  val asm = "6.0"
  val cats = "1.1.0"
  val evilplot = "0.2.0"
  val scalacheck = "1.14.0"
  val scalatest = "3.0.5"
}

// primary modules

lazy val rainierCore = project.
  in(file("rainier-core")).
  settings(name := "rainier-core").
  settings(
    crossScalaVersions := List("2.11.12", "2.12.4")
  ).
  dependsOn(shadedAsm).
  settings(publishSettings)

lazy val rainierPlot = project.
  in(file("rainier-plot")).
  settings(name := "rainier-plot").
  dependsOn(rainierCore).
  settings(
    resolvers += Resolver.bintrayRepo("cibotech", "public"),
    libraryDependencies += "com.cibo" %% "evilplot" % V.evilplot).
  settings(publishSettings)

lazy val rainierCats = project.
  in(file("rainier-cats")).
  settings(name := "rainier-cats").
  dependsOn(rainierCore).
  dependsOn(rainierScalacheck % "test").
  settings(libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core" % V.cats)).
  settings(publishSettings)

lazy val rainierScalacheck = project.
  in(file("rainier-scalacheck")).
  settings(name := "rainier-scalacheck").
  dependsOn(rainierCore).
  settings(libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core" % V.cats,
    "org.scalacheck" %% "scalacheck" % V.scalacheck)).
  settings(publishSettings)

// documentation modules

lazy val rainierDocs = project.
  in(file("rainier-docs")).
  settings(name := "rainier-docs").
  enablePlugins(TutPlugin).
  dependsOn(rainierCore, rainierPlot).
  settings(
    scalacOptions in Tut ~= {
      _.filterNot(Set("-Ywarn-unused-import", "-Yno-predef", "-Ywarn-unused:imports"))
    },
    // todo: uncomment once docs generation is deterministic
    // tutTargetDirectory := (baseDirectory in LocalRootProject).value / "docs"
  ).
  settings(unpublished)

lazy val rainierExample = project.
  in(file("rainier-example")).
  settings(name := "rainier-example").
  dependsOn(rainierPlot).
  settings(unpublished)

// test modules

lazy val rainierBenchmark = project.
  in(file("rainier-benchmark")).
  settings(name := "rainier-benchmark").
  enablePlugins(JmhPlugin).
  settings(
    scalacOptions ~= (_.filterNot(Set(
      "-Ywarn-value-discard")))).
  dependsOn(rainierCore).
  settings(unpublished)

lazy val rainierTests = project.
  in(file("rainier-tests")).
  settings(name := "rainier-tests").
  dependsOn(
    rainierCore,
    rainierPlot,
    rainierCats,
    rainierScalacheck
  ).
  settings(libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % V.scalatest,
    "org.scalacheck" %% "scalacheck" % V.scalacheck,
    "org.typelevel" %% "cats-laws" % V.cats,
    "org.typelevel" %% "cats-testkit" % V.cats)).
  settings(unpublished)

// shaded asm dep trickery

/* publishable project with the shaded deps */
lazy val shadedAsm = project.
  in(file(".rainier-shaded-asm")).
  settings(name := "rainier-shaded-asm").
  settings(
    exportJars := true,
    packageBin in Compile := (assembly in asmDeps).value,
    crossScalaVersions := List("2.11.12", "2.12.4")).
  settings(publishSettings)

/* phantom project to bundle deps for shading */
lazy val asmDeps = project.
  in(file(".asm-deps")).
  settings(libraryDependencies ++= Seq(
    "org.ow2.asm" % "asm" % V.asm,
    "org.ow2.asm" % "asm-tree" % V.asm)).
  settings(
    crossScalaVersions := List("2.11.12", "2.12.4"),
    skip in publish := true,
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(
      includeBin = false,
      includeDependency = true,
      includeScala = false),
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("org.objectweb.asm.**" -> "com.stripe.rainier.internal.asm.@1").inAll),
    assemblyMergeStrategy in assembly := {
      case "module-info.class" => MergeStrategy.discard
      case other =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(other)
    },
  )

organization in ThisBuild := "com.stripe"
scalaVersion in ThisBuild := "2.12.4"

val unpublished = Seq(publish := {}, publishLocal := {}, publishArtifact := false)

val publishSettings = Seq(
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  homepage := Some(url("https://github.com/stripe/rainier")),
  licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  autoAPIMappings := true,
  apiURL := Some(url("https://stripe.github.io/rainier/api/")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/stripe/rainier"),
      "scm:git:git@github.com:stripe/rainier.git"
    )
  ),
  developers := List(
    Developer("avibryant", "Avi Bryant", "", url("https://twitter.com/avibryant"))
  ),
)

scalafmtOnCompile in ThisBuild := true
