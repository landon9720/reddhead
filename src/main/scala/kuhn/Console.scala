package kuhn

import spray.can.client.HttpClient
import spray.io._

import akka.actor.{Actor, Props, ActorSystem}
import spray.client.HttpConduit
import HttpConduit._

import scala.concurrent.duration._

import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.JsonNode
import akka.contrib.throttle.TimerBasedThrottler
import akka.contrib.throttle.Throttler.{SetRate, SetTarget, Rate}

object MiscellaneousUtilities {

	val mapper = new ObjectMapper

	implicit class rich_string(s:String) {
		def tab = s.split("\n").mkString("  ", "\n  ", "")
		def toJson = mapper.readTree(s)
		def optional = Option(s)
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
	val json:JsonNode
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

class Listing(val json:JsonNode) extends Thing {

	val kind = "Listing"
	val id = "none"
	val name = "none"
	val before = json.path("data").path("before").getTextValue.optional
	val after = json.path("data").path("after").getTextValue.optional

	import collection.JavaConversions._
	val things = for (thing <- json.path("data").path("children")) yield Thing(thing)

	override def toString =
		"listing (before=%s after=%s):\n".format(before.getOrElse("0"), after.getOrElse("0")) +
		things.mkString("\n").tab
}

class More(val json:JsonNode) extends Thing {

	val kind = "more"
	val id = json.path("id").asText
	val name = json.path("name").asText
	val count = json.path("count").asInt
	val parent_id = json.path("parent_id").asText

	import collection.JavaConversions._
	val children = json.path("children").map(_.asText)

	override def toString = "more: (%s) %s".format(count, children.take(10).mkString(","))
}

class Comment(val json:JsonNode) extends Thing {
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

class Link(val json:JsonNode) extends Thing {
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
	import system.dispatcher

	val ioBridge = IOExtension(system).ioBridge()
	val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))

	val conduit = system.actorOf(Props(new HttpConduit(httpClient, "www.reddit.com", 80)))

	val throttler = system.actorOf(Props(new TimerBasedThrottler(Rate(1, 3 seconds))))
	throttler ! SetRate(Rate(10, 1 minute))
	throttler ! SetTarget(Some(conduit))

	val pipeline = sendReceive(throttler)

	case class Subreddit(r:String) {
		val hot = Query("r/%s".format(r))
		val `new` = Query("r/%s/new".format(r))
		val controversial = Query("r/%s/controversial".format(r))
		def top(t:String) = Query("r/%s/top".format(r)).copy(t = Some(t))
		// hour, day, week, month, year, all
		// confidence, top, new, hot, controversial, old, random
	}

	case class Query(
        path:String,
        before:Option[String] = None,
        after:Option[String] = None,
        limit:Int = 2,
        sort:Option[String] = None,
        t:Option[String] = None
	) {
		def previous(listing:Listing):Option[Query] =
			for (before <- listing.before) yield copy(before = listing.before, after = None)
		def next(listing:Listing):Option[Query] =
			for (after <- listing.after) yield copy(before = None, after = listing.after)
		val url:String = "/%s.json?".format(path) +
//			"limit=%d&".format(limit) +
			before.map("before=%s&".format(_)).getOrElse("") +
			after.map("after=%s&".format(_)).getOrElse("") +
			sort.map("sort=%s&".format(_)).getOrElse("") +
			t.map("t=%s&".format(_)).getOrElse("")
	}

	def scroll[T <: Thing](q:Query, pages:Int = 2)(f:T=>Unit) {
		for (httpResponse <- pipeline(Get(q.url))) {
			var listing = new Listing(httpResponse.entity.asString.toJson)
			listing.things.collect { case t:T => t } foreach f
			for (_ <- 0 until pages - 1) {
				for {
					next <- q.next(listing)
					httpResponse <- pipeline(Get(next.url))
				} {
					listing = new Listing(httpResponse.entity.asString.toJson)
					listing.things.collect { case t:T => t } foreach f
				}
			}
		}
	}

	def traverse(linkId:String, t:Option[String] = None)(f:(Seq[Comment], Comment)=>Boolean) {
		def scroll(f:Comment=>Unit) {
			val q = Query("comments/%s".format(linkId), t = t)
			for (httpResponse <- pipeline(Get(q.url))) {
				val json = httpResponse.entity.asString.toJson
				val listing = new Listing(json.get(1))
				listing.things.collect { case c:Comment => c } foreach f
//				for (_ <- 0 until pages - 1) {
//					for {
//						next <- q.next(listing)
//						httpResponse <- pipeline(Get(next.url))
//					} {
//						val listing = new Listing(json.get(1))
//						listing.things.collect { case c:Comment => c } foreach { c => f(c)}
//					}
//				}
			}
		}
		scroll { c =>
			def traverse2(c:Comment, ancestors:Seq[Comment]) {
				val continue = f(ancestors, c)
				if (continue) c.replies.things.foreach {
					case child:Comment => traverse2(child, c +: ancestors)
					case m:More => {
						for (commentId <- m.children) {
							val q = Query("comments/%s/x/%s".format(linkId, commentId))
							for (httpResponse <- pipeline(Get(q.url))) {
								val json = httpResponse.entity.asString.toJson
								val listing = new Listing(json.get(1))
								for (child <- listing.things.collect { case c:Comment => c }) {
									traverse2(child, c +: ancestors)
								}
							}
						}
					}
				}
			}
			traverse2(c, Seq())
		}
	}

//
//	scroll[Link](Subreddit("pics").hot.copy(limit = 2), 1) { l =>
//		println("hot " + l)
//	}
//	scroll[Link](Subreddit("pics").`new`.copy(limit = 2), 1) { l =>
//		println("new " + l)
//	}
//	scroll[Link](Subreddit("pics").controversial.copy(limit = 2), 1) { l =>
//		println("controversial " + l)
//	}
//	scroll[Link](Subreddit("pics").top("").copy(limit = 2), 1) { l =>
//		println("top " + l)
//	}

	// download top images from r/pics
//	import sys.process._
//	val executeCommand = system.actorOf(Props(new Actor {
//		def receive = {
//			case cmd:String => cmd.run
//		}
//	}))
//	scroll(Subreddit("pics").top("year")) { link:Link =>
//		executeCommand ! "wget -o /tmp/wget.log -P /tmp %s".format(link.url)
//	}

	// iterate link comments
	traverse("16716l") { (ancestors, c) =>
		println("-" * ancestors.size + c.body)
		true
	}

	println("SLEEP...")
//	Thread.sleep(30000)
	system.awaitTermination
//	system.shutdown
}