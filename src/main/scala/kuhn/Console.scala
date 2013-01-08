package kuhn

import spray.can.client.HttpClient
import spray.io._

import akka.actor.{Props, ActorSystem}
import spray.client.HttpConduit
import HttpConduit._
import akka.dispatch.Await

import akka.util.duration._

import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.JsonNode

object MiscellaneousUtilities {

	val mapper = new ObjectMapper // is this thing threadsafe?

	implicit def rich_string(s:String) = new {
		def tab = s.split("\n").mkString("  ", "\n  ", "")
		def toJson = mapper.readTree(s)
	}

	implicit def rich_json(j:JsonNode) = new {
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
		(for (listing <- json.getElements) yield new Listing(listing)).toList
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

	override def toString = "listing (before=%s after=%s):\n".format(before.getOrElse("0"), after.getOrElse("0")) + things.mkString("\n").tab
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
	val body = json.path("body").asText
	val link_id = json.path("link_id").asText
	val replies = new Listing(json.path("replies"))

	override def toString = "comment: %s\n%s".format(body, replies.toString.tab)
}

class Link(json:JsonNode) extends Thing {
	val kind = "t3"
	val id = json.path("id").asText
	val name = json.path("name").asText
	val url = json.path("url").asText
	val title = json.path("title").asText
	val ups = json.path("ups").asInt
	val downs = json.path("downs").asInt
	override def toString = "link: %s \"%s\" +%d -%d [%s]".format(url, title, ups, downs, name)
}

// runtime //

object Console extends App {

	implicit val system = ActorSystem()
	val ioBridge = IOExtension(system).ioBridge()
	val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))
	val conduit = system.actorOf(Props(new HttpConduit(httpClient, "www.reddit.com", 80)))

	val pipeline = sendReceive(conduit)

	val jsonString = Await.result(pipeline(Get("/top/.json?limit=20")), 10 seconds).entity.asString
	val json = mapper.readTree(jsonString)
	val listing = new Listing(jsonString.toJson)
	println(listing)

	def next(listing:Listing):Option[Listing] = {
		for (after <- listing.after) yield {
			new Listing(Await.result(pipeline(Get("/top/.json?limit=20&after=" + after)), 10 seconds).entity.asString.toJson)
		}
	}
	println("NEXT!!")
	println(next(listing))

	Thread.sleep(3000)
	system.shutdown
}