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
  crossScalaVersions := List("2.11.12", "2.12.4")
)

scalafmtOnCompile in ThisBuild := true

lazy val root = project.
  in(file(".")).
  aggregate(rainierCore, rainierPlot, rainierExample, rainierBenchmark).
  aggregate(shadedAsm).
  settings(unpublished: _*)

lazy val rainierCore = project.
  in(file("rainier-core")).
  settings(name := "rainier-core").
  enablePlugins(TutPlugin).
  settings(Seq(
    libraryDependencies ++= Seq(
      "commons-io" % "commons-io" % "2.6",
      "org.scalatest" %% "scalatest" % "3.0.5" % Test),
    scalacOptions in Tut ~= {
      _.filterNot(Set("-Ywarn-unused-import", "-Yno-predef", "-Ywarn-unused:imports"))
    }
  )).
  dependsOn(shadedAsm).
  settings(publishSettings)

lazy val rainierPlot = project.
  in(file("rainier-plot")).
  settings(name := "rainier-plot").
  dependsOn(rainierCore).
  settings(
    resolvers += Resolver.bintrayRepo("cibotech", "public"),
    libraryDependencies += "com.cibo" %% "evilplot" % "0.2.0").
  settings(unpublished: _*)

lazy val rainierExample = project.
  in(file("rainier-example")).
  settings(name := "rainier-example").
  dependsOn(rainierPlot).
  settings(unpublished: _*)

lazy val rainierBenchmark = project.
  in(file("rainier-benchmark")).
  settings(name := "rainier-benchmark").
  enablePlugins(JmhPlugin).
  settings(
    scalacOptions ~= (_.filterNot(Set(
      "-Ywarn-value-discard")))).
  dependsOn(rainierCore).
  settings(unpublished: _*)

// publishable project with the shaded deps
lazy val shadedAsm = project.
  in(file(".rainier-shaded-asm")).
  settings(name := "rainier-shaded-asm").
  settings(
    exportJars := true,
    packageBin in Compile := (assembly in asmDeps).value).
  settings(publishSettings)

// phantom project to bundle deps for shading
lazy val asmDeps = project.
  in(file(".asm-deps")).
  settings(
    libraryDependencies += "org.ow2.asm" % "asm" % "6.0",
    libraryDependencies += "org.ow2.asm" % "asm-tree" % "6.0",
  ).
  settings(
    skip in publish := true,
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(
      includeBin = false,
      includeDependency = true,
      includeScala = false,
    ),
    assemblyShadeRules in assembly := Seq(
      ShadeRule.rename("org.objectweb.asm.**" -> "com.stripe.rainier.internal.asm.@1").inAll,
    ),
    assemblyMergeStrategy in assembly := {
      case "module-info.class" => MergeStrategy.discard
      case other =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(other)
    },
  )
