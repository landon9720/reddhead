package kuhn.reddit

import akka.actor.Actor
import java.net.URL
import org.slf4j.LoggerFactory
import uk.co.bigbeeconsultants.http.HttpBrowser
import spray.json._
import DefaultJsonProtocol._
import kuhn._
import graph._
import scalaz._
import Scalaz._

// actor messages
case class GetPage(name: String, url: String)
case class GetLink(name: String)
case class GetComments(link_id: String, depth: Int, limit: Int, comment_id: Option[String], context: Option[Int])
case class GetMore(link_id: String, children: List[String])

// event bus events
case class NewPage(name: String)
case class NewLink(name: String)
case class NewComment(name: String)

class reddit extends Actor {

  private val logger = LoggerFactory.getLogger("reddit")
  import logger._

  val http = new HttpBrowser

  def handleLink(link: Link): Unit = tx {
    tf: TF ⇒
      val node = optNode("name", link.name).getOrElse {
        val node = createNode
        node.setProperty("kind", "link")
        node.setProperty("name", link.name)
        tf += {
          () ⇒ actors.system.eventStream.publish(reddit.NewLink(link.name))
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
          () ⇒ actors.system.eventStream.publish(reddit.NewComment(comment.name))
        }
        node
      }
      node.setProperty("id", comment.id)
      node.setProperty("body", comment.body)
      tf += {
        () ⇒
          for {
            listing ← comment.replies.collect {
              case Left(listing) ⇒ listing
            }
            child ← listing.data.children
          } child.data match {
            case comment: Comment ⇒ handleComment(link_id, comment)
            case more: More ⇒ for (child ← more.children) {
              actors.redditActorRef ! GetComments(link_id, 5, 500, child.some, 0.some)
            }
          }
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
      var url = s"http://www.reddit.com/comments/$link_id.json?depth=$depth&limit=$limit"
      comment_id.map(c ⇒ url += s"&comment=$c")
      context.map(c ⇒ url += s"&context=$c")
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
