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
//http://www.reddit.com/api/morechildren?link=&children=cgfv849
//curl 'http://www.reddit.com/api/morechildren.json' --data 'link_id=t3_21r672&children=cgfv849&depth=2'
import akka.contrib.throttle._, Throttler._
import scalaz._
import Scalaz._

object actors extends App {

  implicit val system = ActorSystem()
  implicit val ec     = system.dispatcher

  val reddit              = {
    val throttler = system.actorOf(Props(classOf[TimerBasedThrottler], 30 msgsPerMinute))
    throttler ! SetTarget(system.actorOf(Props[reddit]).some)
    throttler
  }
  val `new page actor`    = system.actorOf(Props[NewPage])
  val `new story actor`   = system.actorOf(Props[NewStory])
  val `new comment actor` = system.actorOf(Props[NewComment])

  //  val service = system.actorOf(Props[ServiceActor])
  //  implicit val timeout = Timeout(5.seconds)
  //  IO(Http) ? Http.Bind(service, interface = "localhost", port = 9797
  //
  //     case "frontpage"             ⇒ getPage("http://www.reddit.com/.json", `frontpage page name`)
  //    case "frontpage/new"         ⇒ getPage("http://www.reddit.com/new/.json", `frontpage/new page name`)

  //    system.scheduler.schedule(1 second, 60 seconds, reddit, "frontpage")
  //    system.scheduler.schedule(1 second, 60 seconds, reddit, "frontpage/new")

  system.scheduler.schedule(1 second, 60 seconds, reddit,
    GetPage("commentstest", "http://www.reddit.com/r/funny/comments/21r672/the_creation_of_reddit/.json")
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
    case pageName: String ⇒ tx {
      tf: TF ⇒
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
      tx {
        tf: TF ⇒
          val node = getNode(`story name`, storyName)
          val subreddit = node.getProperty(`story subreddit`).asInstanceOf[String]
          val title = node.getProperty(`story title`).asInstanceOf[String]
          val url = node.getProperty(`story url`).asInstanceOf[String]
          info(s"/r/$subreddit $YELLOW$title$RESET $url")
      }
  }
}

class NewComment extends Actor {

  val log = LoggerFactory.getLogger(classOf[NewComment])

  import log._

  def receive = {
    case id: String ⇒
      tx {
        tf: TF ⇒
          val node = getNode("comment_id", id)
          val body = node.getProperty("comment_body").asInstanceOf[String]
          info(s"$CYAN$body$RESET")
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

  def getNode(k: String, v: String) = optNode(k, v).getOrElse(sys.error(s"node $k=$v not found"))

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

object graph extends graph("page_name", "story_name", "comment_id") {
  type TF = collection.mutable.ListBuffer[() ⇒ Unit]
}

case class GetPage(name: String, url: String)
case class GetMore(link_id: String, childrenn: Set[String])

class reddit extends Actor {

  val logger = LoggerFactory.getLogger("reddit")

  import logger._

  case class Listing(
                      kind: String,
                      data: ListingData
                      )


  case class ListingData(
                          children: List[Child],
                          after: Option[String],
                          before: Option[String]
                          )


  case class Child(
                    kind: String, // t3 is a story listing, // t1 is a comment listing
                    data: ChildData
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
                    name: String, // unique id
                    author: String
                    ) extends ChildData


  case class Comment(
                      id: String,
                      body: String,
                      replies: Option[Either[Listing, String]] // wtf reddit!!!
                      ) extends ChildData

  case class More(
                   count: Int,
                   parent_id: String,
                   id: String,
                   name: String,
                   children: List[String]
                   ) extends ChildData

  implicit val moreFormat   : JsonFormat[More]        = jsonFormat1(More)
  implicit val commentFormat: JsonFormat[Comment]     = lazyFormat(jsonFormat3(Comment))
  implicit val storyFormat  : JsonFormat[Story]       = jsonFormat9(Story)
  implicit val childFormat  : JsonFormat[Child]       = new JsonFormat[Child] {
    def read(json: JsValue) = {
      val obj = json.asJsObject
      val kind = obj.fields("kind").asInstanceOf[JsString]
      val data = obj.fields("data").asInstanceOf[JsObject]
      Child(kind.value, kind.value match {
        case "t1" ⇒ commentFormat.read(data)
        case "t3" ⇒ storyFormat.read(data)
        case "more" ⇒ moreFormat.read(data)
        case t ⇒ warn(s"what's this type? $t");
          new ChildData {
            override def toString = t
          }
      })
    }

    def write(obj: Child) = ???
  }
  implicit val dataFormat   : JsonFormat[ListingData] = jsonFormat3(ListingData)
  implicit val pageFormat   : JsonFormat[Listing]     = jsonFormat2(Listing)

  val http = new HttpBrowser

  def handleStory(story: Story): Unit = tx {
    tf: TF ⇒
      val node = optNode(`story name`, story.name).getOrElse {
        val newStoryNode = createNode
        newStoryNode.setProperty(`kind`, `story kind`)
        newStoryNode.setProperty(`story name`, story.name)
        tf += {
          () ⇒ `new story actor` ! story.name
        }
        newStoryNode
      }
      node.setProperty(`story domain`, story.domain)
      node.setProperty(`story url`, story.url)
      node.setProperty(`story title`, story.title)
      node.setProperty(`story self text`, story.selftext)
      node.setProperty(`story ups`, story.ups)
      node.setProperty(`story downs`, story.downs)
      node.setProperty(`story subreddit`, story.subreddit)
      node.setProperty(`story author`, story.author)
  }

  def handleComment(comment: Comment): Unit = tx {
    tf: TF ⇒
      val node = optNode("comment_id", comment.id).getOrElse {
        val newCommentNode = createNode
        newCommentNode.setProperty(`kind`, "comment")
        newCommentNode.setProperty("comment_id", comment.id)
        tf += {
          () ⇒ `new comment actor` ! comment.id
        }
        newCommentNode
      }
      node.setProperty("comment_body", comment.body)
      tf += { () ⇒
        comment.replies.map(_.fold(
          (l: Listing) ⇒ handleListing(l),
          (s: String) ⇒ s match {
            case "" ⇒ // ignore
            case _ ⇒ error(s"WTF [$s]")
          }
        ))
      }
  }

  def handleListing(listing: Listing): Unit = {
    for (child ← listing.data.children) child.data match {
      case story: Story ⇒ handleStory(story)
      case comment: Comment ⇒ handleComment(comment)
      case more: More ⇒ warn("more: " + more)
      case t: ChildData ⇒ warn(s"ignoring $t")
    }
  }

  def receive = {

    case GetPage(name, url) ⇒

      val response = http.get(new URL(url))
      val body = response.body.asString
      val json = body.asJson
      val listings = try {
        List(json.convertTo[Listing])
      } catch {
        case _: DeserializationException ⇒
          json.convertTo[List[Listing]]
      }

      tx {
        tf: TF ⇒
          optNode(`page name`, name).getOrElse {
            val newPageNode = createNode
            newPageNode.setProperty(`page name`, name)
            tf += {
              () ⇒ `new page actor` ! name
            }
            newPageNode
          }
      }

      listings.foreach(handleListing)

    case GetMore(story_id: String, children: Set[String]) ⇒


  }

}
