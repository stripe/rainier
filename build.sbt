import ReleaseTransformations._

lazy val root = project.
  in(file(".")).
  aggregate(rainierBase, rainierCompute, rainierSampler, rainierCore, rainierNotebook).
  aggregate(rainierBenchmark, rainierTest).
  aggregate(shadedAsm).
  settings(commonSettings).
  settings(unpublished).
  settings(
    // crossScalaVersions must be set to Nil on the aggregating project
    crossScalaVersions := Nil
  )

scalafmtOnCompile in ThisBuild := true

lazy val commonSettings = Seq(
  organization:= "com.stripe",
  scalaVersion := "2.12.10",
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  homepage := Some(url("https://github.com/stripe/rainier")),
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  publishMavenStyle := true,
  publishTo := sonatypePublishToBundle.value,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
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
  releaseCrossBuild := true, // true if you cross-build the project for multiple Scala versions
    releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    // For non cross-build projects, use releaseStepCommand("publishSigned")
    releaseStepCommandAndRemaining("+publishSigned"),
    releaseStepCommand("sonatypeBundleRelease"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  ),
  credentials ++= (for {
    username <- Option(System.getenv().get("SONATYPE_USERNAME"))
    password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
  } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq
)

lazy val crossBuildSettings = Seq(
  crossScalaVersions := List("2.13.1", "2.12.10", "2.11.12"),
  releaseCrossBuild := true
)

lazy val unpublished = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  skip in publish := true)

/* dependency versions */
lazy val V = new {
  val asm = "6.0"
  val evilplot = "0.8.1"
  val scalatest = "3.0.8"
  val flogger = "0.3.1"
  val almond = "0.9.0"
  val shadedAsm = "0.2.1"
  val scalameta = "4.2.3"
  val mdoc = "2.1.1"
  val amm = "2.0.4"
}

// primary modules


lazy val rainierBase = project.
  in(file("rainier-base")).
  settings(name := "rainier-base").
  settings(commonSettings).
  settings(crossBuildSettings).
  settings(
    libraryDependencies ++= Seq(
      "com.google.flogger" % "flogger" % V.flogger,
      "com.google.flogger" % "flogger-system-backend" % V.flogger))

lazy val rainierCompute = project.
  in(file("rainier-compute")).
  dependsOn(rainierBase).
  settings(name := "rainier-compute").
  settings(commonSettings).
  settings(crossBuildSettings).
  settings(
    libraryDependencies ++= Seq(
      "com.stripe" % "rainier-shaded-asm_6.0" % V.shadedAsm)
  )

lazy val rainierSampler = project.
  in(file("rainier-sampler")).
  dependsOn(rainierBase).
  settings(name := "rainier-sampler").
  settings(commonSettings).
  settings(crossBuildSettings)

lazy val rainierCore = project.
  in(file("rainier-core")).
  settings(name := "rainier-core").
  dependsOn(rainierCompute, rainierSampler).
  settings(commonSettings).
  settings(crossBuildSettings)

lazy val rainierNotebook = project.
  in(file("rainier-notebook")).
  dependsOn(rainierCompute, rainierCore, rainierSampler).
  settings(name := "rainier-notebook").
  settings(commonSettings).
  settings(
    resolvers ++=
      Seq("jitpack" at "https://jitpack.io"),
    libraryDependencies ++=
      Seq(
        "io.github.cibotech" %% "evilplot" % V.evilplot,
        "org.scalameta" %% "scalameta" % V.scalameta,
        "org.scalameta" %% "mdoc" % V.mdoc,
        "com.lihaoyi" % "ammonite-repl_2.12.10" % V.amm,
        "sh.almond" %% "jupyter-api" % V.almond)
  )


lazy val rainierBenchmark = project.
  in(file("rainier-benchmark")).
  settings(name := "rainier-benchmark").
  enablePlugins(JmhPlugin).
  settings(
    scalacOptions ~= (_.filterNot(Set(
      "-Ywarn-value-discard")))).
  dependsOn(rainierCore).
  settings(commonSettings).
  settings(unpublished)

lazy val rainierTest = project.
  in(file("rainier-test")).
  settings(name := "rainier-test").
  dependsOn(
    rainierCore
  ).
  settings(commonSettings).
  settings(libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % V.scalatest)).
  settings(unpublished)

lazy val rainierDecompile = project.
  in(file("rainier-decompile")).
  settings(name := "rainier-decompile").
  dependsOn(rainierCore).
  settings(commonSettings).
  settings(unpublished)

// documentation modules

lazy val docs = project.
  in(file("rainier-docs")).
  settings(moduleName := "rainier-docs").
  enablePlugins(MdocPlugin, DocusaurusPlugin).
  dependsOn(
    rainierCore,
    rainierNotebook,
  ).
  settings(commonSettings).
  settings(
   mdocVariables := Map(
     "VERSION" -> "0.3.3"
   )
  ).
  settings(unpublished)

// shaded asm dep trickery

/* publishable project with the shaded deps */
lazy val shadedAsm = project.
  in(file(".rainier-shaded-asm")).
  // note: bump version suffix when the shaded asm jar needs to be
  // republished for a particular version of the underlying asm lib
  settings(name := "rainier-shaded-asm_6.0").
  settings(moduleName := "rainier-shaded-asm_6.0").
  settings(commonSettings).
  settings(
    crossPaths := false,
    autoScalaLibrary := false,
    exportJars := true,
    packageBin in Compile := (assembly in asmDeps).value,
    releaseVersion := { ver => ver }
  )

/* phantom project to bundle deps for shading */
lazy val asmDeps = project.
  in(file(".asm-deps")).
  settings(libraryDependencies ++= Seq(
    "org.ow2.asm" % "asm" % V.asm,
    "org.ow2.asm" % "asm-tree" % V.asm)).
  settings(commonSettings).
  settings(
    crossPaths := false,
    autoScalaLibrary := false,
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
    }
  ).
  settings(unpublished)
