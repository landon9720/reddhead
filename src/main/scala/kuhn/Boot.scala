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

object Boot extends App {
  implicit val system = ActorSystem("on-spray-can")
  val service = system.actorOf(Props[ServiceActor], "service-actor")
  implicit val timeout = Timeout(5.seconds)
  IO(Http) ? Http.Bind(service, interface = "localhost", port = 9797)
}

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
            <html><body></body></html>
          }
        }
      }
    }
  }
}

object console extends App {

  val `kind` = "kind"
  val `page kind`  = "page"
  val `story kind` = "story"

  // page
  val `page name` = "page_name"
  val `frontpage page name` = "frontpage"

  // story
  val `story name`      = "story_name"
  val `story domain`    = "story_domain"
  val `story title`     = "story_title"
  val `story self text` = "story_selftext"
  val `story ups`       = "story_ups"
  val `story downs`     = "story_downs"
  val `story subreddit` = "story_subreddit"
  val `story author`    = "story_author"

  val graph = new graph(`page name`, `story name`)
  import graph._

  val `child relationship type` = DynamicRelationshipType.withName("child")

  val logger = LoggerFactory.getLogger("console")
  import logger._

  try tx {

    val page = reddit.frontpage

    val pageNode = ø(`page name`, `frontpage page name`).getOrElse {
      val newPageNode = ç
      newPageNode.setProperty(`page name`, `frontpage page name`)
      newPageNode
    }

    for (child ← page.data.children) {
      val data = child.data
      val node = ç
      pageNode.createRelationshipTo(node, `child relationship type`)
      node.setProperty(`kind`, `story kind`)
      node.setProperty(`story name`, data.name)
      node.setProperty(`story domain`, data.domain)
      node.setProperty(`story title`, data.title)
      node.setProperty(`story self text`, data.selftext)
      node.setProperty(`story ups`, data.ups)
      node.setProperty(`story downs`, data.downs)
      node.setProperty(`story subreddit`, data.subreddit)
      node.setProperty(`story author`, data.author)
    }

    for ((node, i) ← å.zipWithIndex) {
      val props = node.getPropertyKeys.asScala.map(k ⇒ s"$k=${node.getProperty(k)}").mkString("\n")
      info(s"\nNODE $i ${node.getId}\n$props")
    }
  }

  finally shutdown()
}

class graph(indexes: String*) {

  def tx[T](f: ⇒ T): T = {
    val tx = graph.beginTx
    try {
      val t = f
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
  }

  def ç = graph.createNode
  def ©(k: String, v: String) = ø(k, v).getOrElse(sys.error("bam!"))
  def ø(k: String, v: String) = Option(index.get(k, v).getSingle)
  def å = GlobalGraphOperations.at(graph).getAllNodes.asScala

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

object reddit {

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
    title: String,
    selftext: String,
    ups: Int,
    downs: Int,
    subreddit: String,
    name: String,   // unique id
    author: String
  )

  implicit val childDataFormat = jsonFormat8(ChildData)
  implicit val childFormat = jsonFormat2(Child)
  implicit val dataFormat = jsonFormat1(Data)
  implicit val pageFormat = jsonFormat2(Page)

  val h = new HttpBrowser

  def frontpage =
    h.get(new URL("http://reddit.com/.json")).body.asString.asJson.convertTo[Page]

}
