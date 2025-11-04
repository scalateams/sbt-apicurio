name := "sbt-apicurio"
organization := "org.scalateams"
// Version managed by sbt-ci-release via git tags

sbtPlugin := true

scalaVersion := "2.12.19"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.client3" %% "core" % "3.9.7",
  "com.softwaremill.sttp.client3" %% "circe" % "3.9.7",
  "io.circe" %% "circe-core" % "0.14.7",
  "io.circe" %% "circe-generic" % "0.14.7",
  "io.circe" %% "circe-parser" % "0.14.7",
  "org.scalatest" %% "scalatest" % "3.2.18" % Test
)

// Plugin metadata
description := "SBT plugin for Apicurio Schema Registry integration"
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

// Publishing configuration for Maven Central via Sonatype
homepage := Some(url("https://github.com/scalateams/sbt-apicurio"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/scalateams/sbt-apicurio"),
    "scm:git:git@github.com:scalateams/sbt-apicurio.git"
  )
)
developers := List(
  Developer(
    id = "scalateams",
    name = "ScalaTeams",
    email = "team@scalateams.org",
    url = url("https://github.com/scalateams")
  )
)

// Publishing settings - let sbt-ci-release 1.11+ handle Central Portal configuration automatically
// No manual publishMavenStyle, publishTo, or credentials needed

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen"
)
