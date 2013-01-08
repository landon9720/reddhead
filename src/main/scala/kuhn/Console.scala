package kuhn

import spray.can.client.HttpClient
import spray.io._

import akka.actor.{Props, ActorSystem}
import spray.client.HttpConduit
import HttpConduit._

import scala.concurrent.duration._

import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.JsonNode
import concurrent.Future
import akka.contrib.throttle.TimerBasedThrottler
import akka.contrib.throttle.Throttler.{SetTarget, Rate}

object MiscellaneousUtilities {

	val mapper = new ObjectMapper // is this thing threadsafe?

	implicit class rich_string(s:String) {
		def tab = s.split("\n").mkString("  ", "\n  ", "")
		def toJson = mapper.readTree(s)
	}

	implicit class rich_json(j:JsonNode) {
		def pretty = mapper.writerWithDefaultPrettyPrinter.writeValueAsString(j)
	}

}
import MiscellaneousUtilities._

// model //

trait Thing {
	val kind:String
	val id:String
	val name:String
}

object Thing {
	def apply(json:JsonNode):Thing = {
		json.path("kind").asText match {
			case "Listing" => new Listing(json.path("data"))
			case "more" => new More(json.path("data"))
			case "t1" => new Comment(json.path("data"))
			case "t3" => new Link(json.path("data"))
			case _ => throw new RuntimeException("What's this?pn%s".format(json.pretty))
		}
	}
}

object Listings {
	def apply(json:JsonNode):List[Listing] = {
		import collection.JavaConversions._
		(for (listing <- json) yield new Listing(listing)).toList
	}
}

class Listing(json:JsonNode) extends Thing {

	val kind = "Listing"
	val id = "none"
	val name = "none"
	val before = Option(json.path("data").path("before").asText)
	val after = Option(json.path("data").path("after").asText)

	import collection.JavaConversions._
	val things = for (thing <- json.path("data").path("children")) yield Thing(thing)

	override def toString =
		"listing (before=%s after=%s):\n".format(before.getOrElse("0"), after.getOrElse("0")) +
		things.mkString("\n").tab
}

class More(json:JsonNode) extends Thing {

	val kind = "more"
	val id = json.path("id").asText
	val name = json.path("name").asText
	val count = json.path("count").asInt
	val parent_id = json.path("parent_id").asText

	import collection.JavaConversions._
	val children = json.path("children").map(_.asText)

	override def toString = "more: (%s) %s".format(count, children.take(10).mkString(","))
}

class Comment(json:JsonNode) extends Thing {
	val kind = "t1"
	val id = json.path("id").asText
	val name = json.path("name").asText
	val author = json.path("author").asText
	val body = json.path("body").asText
	val link_id = json.path("link_id").asText
	val parent_id = json.path("parent_id").asText
	val subreddit = json.path("subreddit").asText
	val replies = new Listing(json.path("replies"))
	val ups = json.path("ups").asInt
	val downs = json.path("downs").asInt
	val created = json.path("created").asLong
	val created_utc = json.path("created_utc").asLong

	override def toString = "comment: %s\n%s".format(body, replies.toString.tab)
}

class Link(json:JsonNode) extends Thing {
	val kind = "t3"
	val id = json.path("id").asText
	val name = json.path("name").asText
	val author = json.path("author").asText
	val domain = json.path("domain").asText
	val is_self = json.path("is_self").asBoolean
	val num_comments = json.path("num_comments").asInt
	val over_18 = json.path("over_18").asBoolean
	val permalink = json.path("permalink").asText
	val score = json.path("score").asInt
	val selftext = json.path("selftext").asText
	val subreddit = json.path("subreddit").asText
	val thumbnail = json.path("thumbnail").asText
	val title = json.path("title").asText
	val url = json.path("url").asText
	val ups = json.path("ups").asInt
	val downs = json.path("downs").asInt
	val created = json.path("created").asLong
	val created_utc = json.path("created_utc").asLong
	override def toString = "link: %s \"%s\" +%d -%d [%s]".format(url, title, ups, downs, name)
}

// runtime //

object Console extends App {

	implicit val system = ActorSystem()
	val ioBridge = IOExtension(system).ioBridge()
	val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))

	val throttler = system.actorOf(Props(new TimerBasedThrottler(Rate(30, 1 minute))))
	throttler ! SetTarget(Some(httpClient))

	val conduit = system.actorOf(Props(new HttpConduit(throttler, "www.reddit.com", 80)))

	val pipeline = sendReceive(conduit)

	case class Query(path:String, before:Option[String] = None, after:Option[String] = None, limit:Int = 10, sort:Option[String] = None) {
		def previous(listing:Listing):Query = copy(before = Some(listing.before.get), after = None)
		def next(listing:Listing):Query = copy(before = None, after = Some(listing.after.get))
	}

	def links(q:Query):Future[Listing] = {
		val url = "/%s.json?limit=%d&".format(q.path, q.limit) +
			q.before.map("before=%s&".format(_)).getOrElse("") +
			q.after.map("after=%s&".format(_)).getOrElse("")
			q.sort.map("sort=%s&".format(_)).getOrElse("")
		println("URL: " + url)
		for (httpResponse <- pipeline(Get(url))) yield new Listing(httpResponse.entity.asString.toJson)
	}

	val q = Query("r/pics/controversial")

	for (listing <- links(q)) {
		println("page 1")
		println(listing.toString)
		for (listing <- links(q.next(listing))) {
			println("page 2")
			println(listing.toString)
		}
	}

	println("SLEEP...")
	Thread.sleep(5000)
	system.shutdown
}