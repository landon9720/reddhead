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
    "com.typesafe.akka"       %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"       %%  "akka-testkit"  % akkaV   % "test",
    "org.specs2"              %%  "specs2-core"   % "2.3.7" % "test",
    "uk.co.bigbeeconsultants" %%  "bee-client"    % "0.21.+"
  )
}

Revolver.settings
