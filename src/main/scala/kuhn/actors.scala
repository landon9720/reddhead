package kuhn

import akka.actor.{ActorSystem, Props}
import org.slf4j.LoggerFactory
import akka.contrib.throttle._
import akka.contrib.throttle.Throttler._
import scalaz._
import Scalaz._
import akka.actor.Actor
import Console._
import graph._
import reddit._

object actors extends App {

  implicit val system = ActorSystem()
  implicit val ec     = system.dispatcher

  val redditActorRef           = {
    val throttler = system.actorOf(Props(classOf[TimerBasedThrottler], 6 msgsPerMinute)) // limit is 30
    throttler ! SetTarget(system.actorOf(Props[reddit]).some)
    throttler
  }

  val log = LoggerFactory.getLogger("actors")
  import log._

  system.eventStream.subscribe(system.actorOf(Props(new Actor {
    def receive = {
      case reddit.NewPage(pageName) ⇒ tx {
        tf: TF ⇒
          val title = getNode("name", pageName).getProperty("name").asInstanceOf[String]
          info(s"new page $YELLOW$title$RESET")
      }
    }
  })), classOf[reddit.NewPage])

  system.eventStream.subscribe(system.actorOf(Props(new Actor {
    def receive = {
      case reddit.NewLink(name) ⇒
        tx {
          tf: TF ⇒
            val node = getNode("name", name)
            val subreddit = node.getProperty("subreddit").asInstanceOf[String]
            val title = node.getProperty("title").asInstanceOf[String]
            val url = node.getProperty("url").asInstanceOf[String]
            info(s"new link /r/$subreddit $YELLOW$title$RESET $url")
        }
    }
  })), classOf[reddit.NewLink])

  system.eventStream.subscribe(system.actorOf(Props(new Actor {
    def receive = {
      case reddit.NewComment(name) ⇒
        tx {
          tf: TF ⇒
            val node = getNode("name", name)
            val body = node.getProperty("body").asInstanceOf[String].replaceAll( """\n""", " ")
            info(s"new comment $CYAN$body$RESET")
        }
    }
  })), classOf[reddit.NewComment])

  redditActorRef ! GetPage("frontpage", "http://www.reddit.com/.json")
  redditActorRef ! GetLink("t3_21r672")
  redditActorRef ! GetComments("21r672", 1, 10, none, none)

  system.registerOnTermination {
    println("bye!")
    graph.shutdown()
  }
}




