package kuhn

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import uk.co.bigbeeconsultants.http.HttpBrowser
import uk.co.bigbeeconsultants.http.request.RequestBody
import java.net.URL
import spray.json._
import DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._
import org.slf4j.LoggerFactory
import org.neo4j.graphdb._
import factory._
import org.neo4j.tooling.GlobalGraphOperations
import collection.JavaConverters._
import graph._
import scala.util.Try

//http://www.reddit.com/api/morechildren?link=&children=cgfv849
//curl 'http://www.reddit.com/api/morechildren.json' --data 'link_id=t3_21r672&children=cgfv849&depth=2'

import akka.contrib.throttle._, Throttler._
import scalaz._
import Scalaz._

object actors extends App {

  implicit val system = ActorSystem()
  implicit val ec     = system.dispatcher

  val reddit           = {
    val throttler = system.actorOf(Props(classOf[TimerBasedThrottler], 30 msgsPerMinute))
    throttler ! SetTarget(system.actorOf(Props[reddit]).some)
    throttler
  }
  val `new page actor` = system.actorOf(Props[NewPage])
  val newLinkActor     = system.actorOf(Props[NewStory])
  val newCommentActor  = system.actorOf(Props[NewComment])

  //  val service = system.actorOf(Props[ServiceActor])
  //  implicit val timeout = Timeout(5.seconds)
  //  IO(Http) ? Http.Bind(service, interface = "localhost", port = 9797
  //
  //     case "frontpage"             ⇒ getPage("http://www.reddit.com/.json", `frontpage page name`)
  //    case "frontpage/new"         ⇒ getPage("http://www.reddit.com/new/.json", `frontpage/new page name`)

  //    system.scheduler.schedule(1 second, 60 seconds, reddit, "frontpage")
  //    system.scheduler.schedule(1 second, 60 seconds, reddit, "frontpage/new")

  //  reddit ! GetMore("foo", List("bar"))
  //

//  reddit ! GetLink("t3_21r672")
//  reddit ! GetComments("21r672")
  reddit ! GetMore("t3_21r672", List("cgfxaaz"))


  //    GetPage("commentstest", "http://www.reddit.com/r/funny/comments/21r672/the_creation_of_reddit/.json")
  //  )

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
        val title = getNode("name", pageName).getProperty("name").asInstanceOf[String]
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
          val node = getNode("name", storyName)
          val subreddit = node.getProperty("subreddit").asInstanceOf[String]
          val title = node.getProperty("title").asInstanceOf[String]
          val url = node.getProperty("url").asInstanceOf[String]
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
          val body = node.getProperty("comment_body").asInstanceOf[String].replaceAll( """\n""", " ")
          info(s"$CYAN$body$RESET")
      }
  }
}

class graph(indexes: String*) {

  //  val `child relationship type` = DynamicRelationshipType.withName("child")

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

object graph extends graph("name") {
  type TF = collection.mutable.ListBuffer[() ⇒ Unit]
}

case class GetPage(name: String, url: String)

case class GetLink(link_fullname: String)

case class GetComments(link_id: String)

case class GetMore(link_id: String, children: List[String])

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

  case class Link(
    name: String, // unique id
    id: String,
    domain: String,
    url: String,
    title: String,
    selftext: String,
    ups: Int,
    downs: Int,
    subreddit: String,
    author: String
    ) extends ChildData


  case class Comment(
    name: String,
    id: String,
    body: String,
    ups: Int,
    downs: Int,
    replies: Option[Either[Listing, String]] // wtf reddit!!!
    ) extends ChildData

  case class More(
    name: String,
    id: String,
    count: Int,
    parent_id: String,
    children: List[String]
    ) extends ChildData

  def tryFormat[T](jsonFormat: JsonFormat[T]) = new JsonFormat[T] {
    def read(json: JsValue) = try {
      jsonFormat.read(json)
    } catch {
      case ex: Exception ⇒
        error(s"exception parsing JSON\n${json.prettyPrint}")
        throw ex
    }
    def write(obj: T) = ???
  }

  implicit val moreFormat   : JsonFormat[More]    = jsonFormat5(More)
  implicit val commentFormat: JsonFormat[Comment] = lazyFormat(jsonFormat6(Comment))
  implicit val linkFormat   : JsonFormat[Link]    = jsonFormat10(Link)

  implicit val childFormat: JsonFormat[Child]       = new JsonFormat[Child] {
    def read(json: JsValue) = {
      val obj = json.asJsObject
      val kind = obj.fields("kind").asInstanceOf[JsString]
      val data = obj.fields("data").asInstanceOf[JsObject]
      Child(kind.value, kind.value match {
        case "t1" ⇒ commentFormat.read(data)
        case "t3" ⇒ linkFormat.read(data)
        case "more" ⇒ moreFormat.read(data)
        case t ⇒ warn(s"what's this type? $t");
          new ChildData {
            override def toString = t
          }
      })
    }
    def write(obj: Child) = ???
  }
  implicit val dataFormat : JsonFormat[ListingData] = jsonFormat3(ListingData)
  implicit val pageFormat : JsonFormat[Listing]     = tryFormat(jsonFormat2(Listing))

  val http = new HttpBrowser

  def handleLink(link: Link): Unit = tx {
    tf: TF ⇒
      val node = optNode("name", link.name).getOrElse {
        val node = createNode
        node.setProperty("kind", "link")
        node.setProperty("name", link.name)
        tf += {
          () ⇒ newLinkActor ! link.name
        }
        node
      }
      node.setProperty("id", link.id)
      node.setProperty("domain", link.domain)
      node.setProperty("url", link.url)
      node.setProperty("title", link.title)
      node.setProperty("selftext", link.selftext)
      node.setProperty("ups", link.ups)
      node.setProperty("downs", link.downs)
      node.setProperty("subreddit", link.subreddit)
      node.setProperty("author", link.author)
  }

  def handleComment(link_id: String, comment: Comment): Unit = tx {
    tf: TF ⇒
      val node = optNode("name", comment.name).getOrElse {
        val node = createNode
        node.setProperty("kind", "comment")
        node.setProperty("name", comment.name)
        tf += {
          () ⇒ newCommentActor ! comment.name
        }
        node
      }
      node.setProperty("id", comment.id)
      node.setProperty("body", comment.body)
      tf += {
        () ⇒
          comment.replies.map(_.fold(
            (l: Listing) ⇒ handleListing(link_id.some, l.data),
            (s: String) ⇒ s match {
              case "" ⇒ // ignore
              case _ ⇒ error(s"WTF [$s]")
            }
          ))
      }
  }

  def handleListing(link_id: Option[String], listing: ListingData): Unit = {
    debug(s"handleListing $link_id $listing")
    for (child ← listing.children) handleChild(link_id, child)
  }

  def handleChild(link_id: Option[String], child: Child): Unit = (link_id, child.data) match {
    case (_, story: Link) ⇒ handleLink(story)
    case (Some(link_id), comment: Comment) ⇒ handleComment(link_id, comment)
    case (_, comment: Comment) ⇒ error("i don't know link_id so i can't get comments")
    case (Some(link_id), more: More) ⇒ warn(s"handleListing $link_id $more"); reddit ! GetMore("t3_" + link_id, more.children)
    case (_, more: More) ⇒ error("i don't know link_id so i can't get more")
    case (_, t: ChildData) ⇒ warn(s"ignoring $t")
  }

  def receive = {

    case GetPage(name, url) ⇒ {
      val response = http.get(new URL(url))
      val body = response.body.asString
      val json = body.asJson
      json.convertTo[List[Listing]].map(l ⇒ handleListing(none, l.data))

      //      tx {
      //        tf: TF ⇒
      //          optNode(`page name`, name).getOrElse {
      //            val newPageNode = createNode
      //            newPageNode.setProperty(`page name`, name)
      //            tf += {
      //              () ⇒ `new page actor` ! name
      //            }
      //            newPageNode
      //          }
      //      }

      //      val listing = try {
      //        json.convertTo[Listing]
      //      } catch {
      //        case e: DeserializationException ⇒ sys.error("more than one listing")
      //          json.convertTo[List[Listing]]
      //      }

    }

    case GetLink(link_name) ⇒ {
      val url = s"http://www.reddit.com/by_id/$link_name.json"
      val response = http.get(new URL(url))
      val body = response.body.asString
      val json = body.asJson
      handleListing(link_name.some, json.convertTo[Listing].data)
    }

    case GetComments(link_id) ⇒ {
      val url = s"http://www.reddit.com/comments/$link_id.json"
      val response = http.get(new URL(url))
      val body = response.body.asString
      val json = body.asJson
      for (listing ← json.convertTo[List[Listing]]) handleListing(link_id.some, listing.data)
    }

    case GetMore(link_name, children) ⇒ {
      assert(link_name.startsWith("t3_"))
      val response = http.post(new URL("http://www.reddit.com/api/morechildren"), RequestBody(Map(
        "link_id" → link_name, // wtf reddit !!!
        "children" → children.mkString(",")
      )).some)
      println(s"*** ${response.status}")
      val body = response.body.asString
      val json = body.asJson
      println(s"*** ${response.status} ${json.prettyPrint}")
      for (child ← json.extract[List[Child]]('jquery / element(14) / element(3) / element(0)))
        handleChild(link_name.some, child)
    }

  }

}
