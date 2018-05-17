organization in ThisBuild := "com.stripe"
scalaVersion in ThisBuild := "2.12.6"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
)

val unpublished = Seq(publish := (), publishLocal := (), publishArtifact := false)

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
  )
)

scalafmtOnCompile in ThisBuild := true

lazy val root = project.
  in(file(".")).
  dependsOn(rainierCore).
  aggregate(rainierCore, rainierExample, rainierBenchmark).
  settings(unpublished: _*)

lazy val rainierCore = project.
  in(file("rainier-core")).
  enablePlugins(TutPlugin).
  settings(publishSettings).
  settings(
    // https://mvnrepository.com/artifact/commons-io/commons-io
    libraryDependencies += "commons-io" % "commons-io" % "2.6"
  ).
  dependsOn(shadedAsm)

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
      ShadeRule.rename("org.objectweb.asm.**" -> "rainier.internal.asm.@1").inAll,
    ),
    assemblyMergeStrategy in assembly := {
      case "module-info.class" => MergeStrategy.discard
      case other =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(other)
    },
  )

lazy val shadedAsm = project.
  in(file(".shaded-asm")).
  settings(
    name := "rainier-shaded-asm",
    exportJars := true,
    packageBin in Compile := (assembly in asmDeps).value)

lazy val rainierExample = project.
  in(file("rainier-example")).
  dependsOn(rainierCore).
  settings(unpublished: _*)

lazy val rainierBenchmark = project.
  in(file("rainier-benchmark")).
  dependsOn(rainierCore).
  settings(unpublished: _*)
