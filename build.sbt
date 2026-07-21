// Use inThisBuild for settings that should apply to the entire build
inThisBuild(
  List(
    organization := "org.scalateams",
    homepage     := Some(url("https://github.com/scalateams/sbt-apicurio")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers   := List(
      Developer(
        id = "scalateams",
        name = "ScalaTeams",
        email = "team@scalateams.org",
        url = url("https://github.com/scalateams")
      )
    ),
    scmInfo      := Some(
      ScmInfo(
        url("https://github.com/scalateams/sbt-apicurio"),
        "scm:git:git@github.com:scalateams/sbt-apicurio.git"
      )
    )
  )
)

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin) // sets sbtPlugin := true and wires scripted
  .settings(
    name           := "sbt-apicurio",
    scalaVersion   := "3.8.4",
    description    := "SBT plugin for Apicurio Schema Registry integration",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "core"          % "3.9.7",
      "com.softwaremill.sttp.client3" %% "circe"         % "3.9.7",
      "io.circe"                      %% "circe-core"    % "0.14.7",
      "io.circe"                      %% "circe-generic" % "0.14.7",
      "io.circe"                      %% "circe-parser"  % "0.14.7",
      "org.scalatest"                 %% "scalatest"     % "3.2.18" % Test
    ),
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-deprecation",
      "-feature",
      "-unchecked"
    ),
    // Configure POM metadata for Maven Central display
    pomPostProcess := { node =>
      import scala.xml.*
      import scala.xml.transform.*
      new RuleTransformer(new RewriteRule {
        override def transform(n: Node): Seq[Node] = n match {
          case e: Elem if e.label == "name" =>
            <name>SBT Apicurio Plugin</name>
          case _                            => n
        }
      }).transform(node).head
    }
  )
