import sbt._

object Dependencies {

  lazy val scalactic: ModuleID = "org.scalactic" %% "scalactic" % "3.2.16"

  lazy val scalatest: ModuleID = "org.scalatest" %% "scalatest" % "3.2.16" % Test

  lazy val circeCore = "io.circe" %% "circe-core" % "0.14.5"

  lazy val circeGeneric = "io.circe" %% "circe-generic" % "0.14.5"

  lazy val circeParser = "io.circe" %% "circe-parser" % "0.14.5"

  lazy val akka = "com.typesafe.akka" %% "akka-actor-typed" % "2.8.3"

  lazy val akkaStream = "com.typesafe.akka" %% "akka-stream-typed" % "2.8.3"

  lazy val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.5.2"

  lazy val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % "2.8.5" % Test

  lazy val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % "10.5.2" % Test

  lazy val akkaTestkit = "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.8.3" % Test

  lazy val core = "io.github.cake-lier" %% "tuples-space-core" % "1.0.2"
}
