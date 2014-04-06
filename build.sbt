organization  := "kuhn"

version       := "0.1"

scalaVersion  := "2.10.3"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers     += "Big Bee Consultants" at "http://repo.bigbeeconsultants.co.uk/repo"

libraryDependencies ++= {
  val akkaV = "2.3.0"
  val sprayV = "1.3.1"
  Seq(
    "io.spray"                %   "spray-can"     % sprayV,
    "io.spray"                %   "spray-routing" % sprayV,
    "io.spray"                %   "spray-http"    % sprayV,
    "io.spray"                %   "spray-httpx"   % sprayV,
    "io.spray"                %   "spray-util"    % sprayV,
    "io.spray"                %   "spray-client"  % sprayV,
    "io.spray"                %   "spray-testkit" % sprayV  % "test",
    "io.spray"                %%  "spray-json"    % "1.2.5",
    "net.virtual-void"        %%  "json-lenses"   % "0.5.4",
    "com.typesafe.akka"       %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"       %%  "akka-contrib"  % akkaV,
    "com.typesafe.akka"       %%  "akka-testkit"  % akkaV   % "test",
    "org.specs2"              %%  "specs2-core"   % "2.3.7" % "test",
    "uk.co.bigbeeconsultants" %%  "bee-client"    % "0.21.+",
    "org.slf4j"               %   "slf4j-api"     % "1.7.+",
    "ch.qos.logback"          %   "logback-core"  % "1.0.+",
    "ch.qos.logback"          %   "logback-classic" % "1.0.+",
    "org.scalaz"              %%  "scalaz-core"   % "7.0.6",
    "org.neo4j"               %   "neo4j"         % "2.0.0"
  )
}

Revolver.settings
