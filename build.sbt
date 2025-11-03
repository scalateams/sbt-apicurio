name := "sbt-apicurio"
organization := "com.upstartcommerce"
version := "0.1.0-SNAPSHOT"

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

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen"
)
