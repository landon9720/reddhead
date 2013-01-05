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
      scalaVersion := "2.9.2"
      // add other settings here
    )
  )
}
