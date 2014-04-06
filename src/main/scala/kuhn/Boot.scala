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

  reddit ! GetPage("frontpage", "http://www.reddit.com/.json")
  reddit ! GetLink("t3_21r672")
  reddit ! GetComments("21r672", 1, 10, none, none)

  system.registerOnTermination {
    println("bye!")
    graph.shutdown()
  }
}

import actors._
import akka.actor.Actor
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
    case name: String ⇒
      tx {
        tf: TF ⇒
          val node = getNode("name", name)
          val body = node.getProperty("body").asInstanceOf[String].replaceAll( """\n""", " ")
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

case class GetLink(name: String)

case class GetComments(link_id: String, depth: Int, limit: Int, comment_id: Option[String], context: Option[Int])

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
      })
    }
    def write(obj: Child) = ???
  }
  implicit val dataFormat : JsonFormat[ListingData] = jsonFormat3(ListingData)
  implicit val pageFormat : JsonFormat[Listing]     = jsonFormat2(Listing)

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
          for {
            listing ← comment.replies.collect { case Left(listing) ⇒ listing }
            child ← listing.data.children
            if child.data.isInstanceOf[Comment]
            comment = child.data.asInstanceOf[Comment]
          } handleComment(link_id, comment)
      }
  }

  def receive = {

    case GetPage(name, url) ⇒ {
      val response = http.get(new URL(url))
      val body = response.body.asString
      val json = body.asJson
      val listing = json.convertTo[Listing]
      for {
        child ← listing.data.children
        link = child.data.asInstanceOf[Link]
      } handleLink(link)
    }

    case GetLink(link_name) ⇒ {
      val url = s"http://www.reddit.com/by_id/$link_name.json"
      val response = http.get(new URL(url))
      val body = response.body.asString
      val json = body.asJson
      val listing = json.convertTo[Listing]
      for {
        child ← listing.data.children
        link = child.data.asInstanceOf[Link]
      } handleLink(link)
    }

    case GetComments(link_id, depth, limit, comment_id, context) ⇒ {
      val url = s"http://www.reddit.com/comments/$link_id.json?depth=$depth&limit=$limit&${comment_id.map(c ⇒ s"comment=$c").getOrElse("")}${context.map(c ⇒ s"context=$c").getOrElse("")}"
      val response = http.get(new URL(url))
      val body = response.body.asString
      val json = body.asJson
      json.convertTo[List[Listing]] match {
        case List(link, comments) ⇒
          for {
            child ← link.data.children
            link = child.data.asInstanceOf[Link]
          } handleLink(link)
          for {
            child ← comments.data.children
            if child.data.isInstanceOf[Comment]
            comment = child.data.asInstanceOf[Comment]
          } handleComment(link_id, comment)
      }
    }

  }

}
