package kuhn

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import uk.co.bigbeeconsultants.http.HttpBrowser
import java.net.URL
import spray.json._
import DefaultJsonProtocol._

//object Boot extends App {
//  implicit val system = ActorSystem("on-spray-can")
//  val service = system.actorOf(Props[ServiceActor], "service-actor")
//  implicit val timeout = Timeout(5.seconds)
//  IO(Http) ? Http.Bind(service, interface = "localhost", port = 9797)
//}

import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._

class ServiceActor extends Actor with HttpService {
  def actorRefFactory = context
  def receive = runRoute {
    path("") {
      get {
        respondWithMediaType(`text/html`) {
          complete {
            <html>
              <body>
                <h1>Say hello to
                  <i>spray-routing</i>
                  on
                  <i>spray-can</i>
                  !</h1>
              </body>
            </html>
          }
        }
      }
    }
  }
}

case class Page(
  kind: String,
  data: Data
)

case class Data(
  children: List[Child]
)

case class Child(
  kind: String,
  data: ChildData
)

case class ChildData(
  domain: String,
  title: String
)

object console extends App {

  implicit val childDataFormat = jsonFormat2(ChildData)
  implicit val childFormat = jsonFormat2(Child)
  implicit val dataFormat = jsonFormat1(Data)
  implicit val pageFormat = jsonFormat2(Page)

  val h = new HttpBrowser
  val r = h.get(new URL("http://reddit.com/.json"))
  private val x = r.body.asString.asJson.convertTo[Page]
  println(x.data.children.map(_.data.title).mkString("\n"))

}
