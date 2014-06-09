name := "scala-reddit"

organization := "kuhn"

version := "0.2-SNAPSHOT"

scalaVersion := "2.10.0"

resolvers := Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "spray repo" at "http://repo.spray.io"
)

libraryDependencies := Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.0",
  "com.typesafe.akka" %% "akka-contrib" % "2.3.0",
  "io.spray" % "spray-client" % "1.3.1",
  "com.fasterxml" % "jackson-module-scala" % "1.9.3"
)
