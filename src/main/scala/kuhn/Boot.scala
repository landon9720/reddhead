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
import org.slf4j.LoggerFactory
import org.neo4j.graphdb._, factory._
import org.neo4j.tooling.GlobalGraphOperations
import collection.JavaConverters._
import graph._

import akka.contrib.throttle._, Throttler._
import scalaz._
import Scalaz._

object actors extends App {

  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher

  val reddit = {
    val throttler = system.actorOf(Props(classOf[TimerBasedThrottler], 30 msgsPerMinute))
    throttler ! SetTarget(system.actorOf(Props[reddit]).some)
    throttler
  }
  val `new page actor` = system.actorOf(Props[NewPage])
  val `new story actor` = system.actorOf(Props[NewStory])

//  val service = system.actorOf(Props[ServiceActor])
//  implicit val timeout = Timeout(5.seconds)
//  IO(Http) ? Http.Bind(service, interface = "localhost", port = 9797
//
//     case "frontpage"             ⇒ getPage("http://www.reddit.com/.json", `frontpage page name`)
//    case "frontpage/new"         ⇒ getPage("http://www.reddit.com/new/.json", `frontpage/new page name`)

//    system.scheduler.schedule(1 second, 60 seconds, reddit, "frontpage")
//    system.scheduler.schedule(1 second, 60 seconds, reddit, "frontpage/new")
    system.scheduler.schedule(1 second, 60 seconds, reddit, 
      ("commentstest", "http://www.reddit.com/r/funny/comments/21r672/the_creation_of_reddit/.json")
    )

    system.registerOnTermination {
      println("bye!")
      graph.shutdown()
    }
}

import actors._

import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._

//class ServiceActor extends Actor with HttpService {
//  def actorRefFactory = context
//  def receive = runRoute {
//    path("") {
//      get {
//        respondWithMediaType(`text/html`) {
//          complete {
//            <html><body></body></html>
//          }
//        }
//      }
//    }
//  }
//}

import Console._

class NewPage extends Actor {

  val log = LoggerFactory.getLogger(classOf[NewPage])
  import log._

  def receive = {
    case pageName: String ⇒ tx { tf: TF ⇒
      val title = getNode(`page name`, pageName).getProperty(`page name`).asInstanceOf[String]
      info(s"$YELLOW$title$RESET")
    }
  }
}

class NewStory extends Actor {

  val log = LoggerFactory.getLogger(classOf[NewStory])
  import log._

  def receive = {
    case storyName: String ⇒
      tx { tf: TF ⇒
        val node = getNode(`story name`, storyName)
        val subreddit = node.getProperty(`story subreddit`).asInstanceOf[String]
        val title = node.getProperty(`story title`).asInstanceOf[String]
        val url = node.getProperty(`story url`).asInstanceOf[String]
        info(s"/r/$subreddit $YELLOW$title$RESET $url")
      }
  }
}

class graph(indexes: String*) {

  val `kind`       = "kind"
  val `page kind`  = "page"
  val `story kind` = "story"

  // page
  val `page name`               = "page_name"
  val `frontpage page name`     = "frontpage"
  val `frontpage/new page name` = "frontpage/new"

  // story
  val `story name`      = "story_name"
  val `story domain`    = "story_domain"
  val `story url`       = "story_url"
  val `story title`     = "story_title"
  val `story self text` = "story_selftext"
  val `story ups`       = "story_ups"
  val `story downs`     = "story_downs"
  val `story subreddit` = "story_subreddit"
  val `story author`    = "story_author"

  val `child relationship type` = DynamicRelationshipType.withName("child")

  def tx[T](f: TF ⇒ T): T = {
    val tx = graph.beginTx
    val tf = new collection.mutable.ListBuffer[() ⇒ Unit]
    val t = try {
      val t = f(tf)
      tx.success()
      t
    }
    catch {
      case ex: Exception ⇒
      tx.failure()
      throw ex
    }
    finally {
      tx.close()
    }
    tf.foreach(f ⇒ f())
    t
  }

  def createNode = graph.createNode
  def getNode(k: String, v: String) = optNode(k, v).getOrElse(sys.error("bam!"))
  def optNode(k: String, v: String) = Option(index.get(k, v).getSingle)
  def allNodes = GlobalGraphOperations.at(graph).getAllNodes.asScala

  def shutdown() {
    graph.shutdown()
  }

  private val graph = {
    val g = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder("data").newGraphDatabase
    g.index.getNodeAutoIndexer.setEnabled(true)
    indexes.foreach(g.index.getNodeAutoIndexer.startAutoIndexingProperty)
    g
  }

  private val index = graph.index.getNodeAutoIndexer.getAutoIndex
}

object graph extends graph("page_name", "story_name") {
  type TF = collection.mutable.ListBuffer[() ⇒ Unit]
}

class reddit extends Actor {

  case class Page(
    kind: String,
    data: Data
  )

  case class Data(
    children: List[Child[_]],
    after: Option[String],
    before: Option[String]
  )

  case class Child[T <: ChildData](
    kind: String, // t3 is a story listing, // t1 is a comment listing
    data: T
  )

  sealed trait ChildData

  case class Story(
    domain: String,
    url: String,
    title: String,
    selftext: String,
    ups: Int,
    downs: Int,
    subreddit: String,
    name: String,   // unique id
    author: String
  ) extends ChildData

  case class Comment(
    id: String
  ) extends ChildData

  implicit val commentFormat = jsonFormat1(Comment)
  implicit val storyFormat   = jsonFormat9(Story)
  implicit val childFormat     = new JsonReader[Child] {
    def read(json: JsValue) = json.asJsObject.fields("kind").asInstanceOf[JsString].value match {
      case "t1" ⇒ commentFormat.read(json)
      case "t3" ⇒ storyFormat.read(json)
    }
  }jsonFormat2(Child)
  implicit val dataFormat      = jsonFormat3(Data)
  implicit val pageFormat      = jsonFormat2(Page)

  val http = new HttpBrowser

  def getPage(name: String, url: String) = {

    val response = http.get(new URL(url))
    val body = response.body.asString
    val json = body.asJson
    val pages = try {
      List(json.convertTo[Page])
    } catch {
      case _: DeserializationException ⇒
        json.convertTo[List[Page]]
    }

    for (page ← pages ) tx {
      tf: TF ⇒

      val pageNode = optNode(`page name`, name).getOrElse {
        val newPageNode = createNode
        newPageNode.setProperty(`page name`, name)

        tf += { () ⇒ `new page actor` ! name }

        newPageNode
      }

      for (child ← page.data.children) {
        val data = child.data.asInstanceOf[Story]

        val node = optNode(`story name`, data.name).getOrElse {
          val newStoryNode = createNode
          newStoryNode.setProperty(`kind`, `story kind`)
          newStoryNode.setProperty(`story name`, data.name)

          tf += { () ⇒ `new story actor` ! data.name }

          // ?
          pageNode.createRelationshipTo(newStoryNode, `child relationship type`)

          newStoryNode
        }

        node.setProperty(`story domain`, data.domain)
        node.setProperty(`story url`, data.url)
        node.setProperty(`story title`, data.title)
        node.setProperty(`story self text`, data.selftext)
        node.setProperty(`story ups`, data.ups)
        node.setProperty(`story downs`, data.downs)
        node.setProperty(`story subreddit`, data.subreddit)
        node.setProperty(`story author`, data.author)
      }

    }
  }

  def receive = {
    case (name: String, url: String) ⇒ getPage(name, url)
  }
}
