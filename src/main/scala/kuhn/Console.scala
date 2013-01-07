package kuhn

import spray.can.client.HttpClient
import spray.io._

import akka.actor.{Actor, Props, ActorSystem}
import spray.client.HttpConduit
import HttpConduit._

object Console extends App {
  implicit val system = ActorSystem()
//  val ioBridge = IOExtension(system).ioBridge()
//  val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))
//  val conduit = system.actorOf(Props(new HttpConduit(httpClient, "www.reddit.com", 80)))
//  sendReceive(conduit)(Get("/.json")) onComplete {
//    case Left(x) => println(x); system.shutdown
//    case Right(x) => println(x); system.shutdown
//  }

  val h = system.actorOf(Props[HelloWorld])
  h ! User("Landon")




  Thread.sleep(1000)
  system.shutdown
}

case class User(name:String)

class HelloWorld extends Actor {
  protected def receive = {
    case User(name) => println("Hello, %s".format(name))
  }
}
