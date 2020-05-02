// Generated with scalagen

val scalatestVersion = "3.1.1"

val buildSettings = Seq(
  scalaVersion := "2.13.1",
  version := "0.0.2",
  organization := "io.mwielocha",
  scalacOptions ++= Seq(
    "-language:postfixOps",
    "-language:implicitConversions",
    "-deprecation",
    "-feature",
    "-Yrangepos",
    "-language:existentials",
    "-language:higherKinds",
    "-language:postfixOps",
  ),
  scalafmtOnCompile in ThisBuild := true,
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  homepage := Some(url("https://github.com/mwielocha/factorio")),
  scmInfo := Some(
  ScmInfo(url("https://github.com/mwielocha/factorio"),
    "git@github.com:mwielocha/factorio.git")),
  developers := List(
    Developer("mwielocha",
      "Mikolaj Wielocha",
      "mwielocha@icloud.com",
      url("https://github.com/mwielocha"
      )
    )
  ),
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
  publishMavenStyle := true,
  publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
  )
)

lazy val `factorio-annotations` = (project in file("factorio-annotations")).
  settings(buildSettings ++ Seq(
    name := "factorio-annotations",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestVersion
    )
  ))

lazy val `factorio-macro` = (project in file("factorio-macro")).
  settings(buildSettings ++ Seq(
    name := "factorio-macro",
    libraryDependencies ++= Seq(
      "javax.inject" % "javax.inject" % "1",
      "org.scalatest" %% "scalatest" % scalatestVersion
    )
  )).dependsOn(`factorio-annotations` % "compile->compile;test->test")

lazy val `factorio-core` = (project in file("factorio-core")).
  settings(buildSettings ++ Seq(
    name := "factorio-core",
    libraryDependencies ++= Seq(
      "com.chuusai" %% "shapeless" % "2.3.3",
      "org.scalatest" %% "scalatest" % scalatestVersion % Test,
    )
  )).dependsOn(`factorio-annotations`, `factorio-macro` % "compile->compile;test->test")

lazy val factorio = (project in file("."))
  .aggregate(`factorio-annotations`, `factorio-core`, `factorio-macro`)


