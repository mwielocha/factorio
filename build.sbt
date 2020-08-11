// Generated with scalagen

val scalatestVersion = "3.1.1"

val buildSettings = Seq(
  scalaVersion := "2.13.3",
  version := "0.3.0",
  organization := "io.mwielocha",
  organizationName := "mwielocha",
  organizationHomepage := Some(url("http://mwielocha.io/")),
  scalacOptions ++= Seq(
    "-language:postfixOps",
    "-language:implicitConversions",
    "-deprecation",
    "-feature",
    "-Yrangepos",
    "-language:existentials",
    "-language:higherKinds",
    "-language:postfixOps",
    "-Xmacro-settings:factorio-verbose,factorio-debug"
  ),
  scalafmtOnCompile in ThisBuild := true,
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  homepage := Some(url("https://github.com/mwielocha/factorio")),
  description := "Tiny, compile time dependency injection framework for Scala.",
  scmInfo := Some(
  ScmInfo(url("https://github.com/mwielocha/factorio"),
    "scm:git@github.com:mwielocha/factorio.git")),
  developers := List(
    Developer("mwielocha",
      "Mikolaj Wielocha",
      "mwielocha@icloud.com",
      url("https://github.com/mwielocha")
    )
  ),
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
    else Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  credentials += Credentials(Path.userHome / ".sbt" / ".sonatype_credentials")
)

lazy val `factorio-annotations` = (project in file("factorio-annotations")).
  settings(buildSettings ++ Seq(
    name := "factorio-annotations"
  ))

lazy val `factorio-macro` = (project in file("factorio-macro")).
  settings(buildSettings ++ Seq(
    name := "factorio-macro",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  )).dependsOn(`factorio-annotations` % "compile->compile;test->test")

lazy val `factorio-core` = (project in file("factorio-core")).
  settings(buildSettings ++ Seq(
    name := "factorio-core",
    libraryDependencies ++= Seq(
      "javax.inject" % "javax.inject" % "1" % Test,
      "org.scalatest" %% "scalatest" % scalatestVersion % Test,
    )
  )).dependsOn(`factorio-annotations`, `factorio-macro` % "compile->compile;test->test")

lazy val factorio = (project in file("."))
  .settings(buildSettings ++ Seq(packagedArtifacts := Map.empty))
  .aggregate(`factorio-annotations`, `factorio-core`, `factorio-macro`)


