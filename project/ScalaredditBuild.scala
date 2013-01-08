import sbt._
import sbt.Keys._

object ScalaredditBuild extends Build {

  lazy val scalareddit = Project(
    id = "scala-reddit",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      name := "scala-reddit",
      organization := "kuhn",
      version := "0.1-SNAPSHOT",
      scalaVersion := "2.9.2",
      resolvers := Seq(
        "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
        "spray repo" at "http://repo.spray.io"
      ),
      libraryDependencies := Seq(
        "com.typesafe.akka" % "akka-actor" % "2.0.5",
        "io.spray" % "spray-client" % "1.0-M7",
//        "io.spray" %%  "spray-json" % "1.2.3" cross CrossVersion.full,
        "com.fasterxml" % "jackson-module-scala" % "1.9.3",
        "org.scalatest" %% "scalatest" % "1.8" % "test"
      ),
      initialCommands in console := "import kuhn._;import Console._;"
    )
  )
}
