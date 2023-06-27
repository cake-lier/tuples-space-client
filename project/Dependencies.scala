import sbt._

object Dependencies {

  lazy val scalactic: ModuleID = "org.scalactic" %% "scalactic" % "3.2.16"

  lazy val scalatest: ModuleID = "org.scalatest" %% "scalatest" % "3.2.16" % Test

  lazy val circeCore = "io.circe" %% "circe-core" % "0.14.5"

  lazy val circeGeneric = "io.circe" %% "circe-generic" % "0.14.5"

  lazy val circeParser = "io.circe" %% "circe-parser" % "0.14.5"

  lazy val core = "io.github.cake-lier" %% "tuples-space-core" % "1.0.1"
}
