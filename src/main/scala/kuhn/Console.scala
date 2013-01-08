package kuhn

import spray.can.client.HttpClient
import spray.io._



import akka.actor.{Props, ActorSystem}
import spray.client.HttpConduit
import HttpConduit._
import akka.dispatch.Await

import akka.util.duration._

import org.codehaus.jackson.map.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.codehaus.jackson.JsonNode

//object RedditJsonProtocol extends DefaultJsonProtocol {
//
//	implicit object ThingFormat extends RootJsonFormat[Thing[_]] {
//		def read(json:JsValue) = json match {
//			case o:JsObject => o.fields.get("kind") match {
//				case Some(JsString(Kinds.Listing)) => json.convertTo[Listing]
//				case Some(JsString(Kinds.link)) => json.convertTo[Link]
//				case Some(JsString(Kinds.comment)) => json.convertTo[Comment]
//			}
//		}
//		def write(obj:Thing[_]) = null
//	}
//
//	implicit val listingFormat:JsonFormat[Listing] = lazyFormat(jsonFormat3(Listing))
//	implicit val linkFormat:JsonFormat[Link] = lazyFormat(jsonFormat15(Link))
//	implicit val commentFormat:JsonFormat[Comment] = lazyFormat(jsonFormat15(Comment))
//
//
//
//}

//import RedditJsonProtocol._

object Tabs {
	implicit def rich_tabs(s:String) = new {
		def tab = s.split("\n").mkString("  ", "\n  ", "")
	}
}

import Tabs._

trait Thing {
	val kind:String
}

object Thing {
	def apply(json:JsonNode):Thing = {
		json.path("kind").asText match {
			case "Listing" => new Listing(json.path("data"))
			case "more" => new More(json.path("data"))
			case "t1" => new Comment(json.path("data"))
			case "t3" => new Link(json.path("data"))
			case _ => sys.error("What's this? %s".format(json.toString))
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
	val before = json.path("before").asText
	val after = json.path("after").asText

	import collection.JavaConversions._
	val things = for (thing <- json.path("data").path("children")) yield Thing(thing)

	override def toString = "listing:\n" + things.mkString("\n").tab
}

class More(json:JsonNode) extends Thing {

	val kind = "more"
	val count = json.path("count").asInt
	val parent_id = json.path("parent_id").asText
	val id = json.path("parent_id").asText
	val name = json.path("name").asText

	import collection.JavaConversions._
	val children = json.path("children").map(_.asText)

	override def toString = "more: (%s) %s".format(count, children.take(10).mkString(","))
}

class Comment(json:JsonNode) extends Thing {
	val kind = "t1"
	val body = json.path("body").asText
	val link_id = json.path("link_id").asText
	val replies = new Listing(json.path("replies"))

	override def toString = "t1 (comment): %s\n%s".format(body, replies.toString.tab)
}

class Link(json:JsonNode) extends Thing {
	val kind = "t3"
	val id = json.path("id").asText
	val name = json.path("name").asText
	val url = json.path("url").asText
	val title = json.path("title").asText
	val ups = json.path("ups").asInt
	val downs = json.path("downs").asInt
	override def toString = "t3 (link): %s \"%s\" +%d -%d [%s]".format(url, title, ups, downs, name)
}

object Console extends App {

	implicit val system = ActorSystem()
	val ioBridge = IOExtension(system).ioBridge()
	val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))
	val conduit = system.actorOf(Props(new HttpConduit(httpClient, "www.reddit.com", 80)))

	val pipeline = sendReceive(conduit)


	val mapper = new ObjectMapper()

//	val jsonString = Await.result(pipeline(Get("/top/.json?limit=10")), 10 seconds).entity.asString
//	val json = mapper.readTree(jsonString)
//	println(mapper.writerWithDefaultPrettyPrinter.writeValueAsString(json))
//
//	val listing = new Listing(json)
//	for (x <- listing.things) x match {
//		case l:Link => println("URL: %s".format(l.url))
//		case _ => println(x)
//	}

	val jsonString = Await.result(pipeline(Get("/comments/1632du.json?limit=3&depth=3&sort=top")), 10 seconds).entity.asString
	val json = mapper.readTree(jsonString)
	println(mapper.writerWithDefaultPrettyPrinter.writeValueAsString(json))

	def p(listing:Listing) {
		println(listing)
		for (m <- listing.things.collect { case m:More => m }) {
			val link_id = listing.things.collectFirst { case c:Comment => c } map(_.link_id) get
			val children = m.children.take(1).mkString(",")
			val url = "/api/morechildren.json?link_id=%s&children=%s".format(link_id, children)
			println("URL", url)
			Thread.sleep(1000)
			val jsonString = Await.result(pipeline(Post(url)), 10 seconds).entity.asString
			println("more!\n" + mapper.writerWithDefaultPrettyPrinter.writeValueAsString(mapper.readTree(jsonString)))
		}
	}

	Listings(json).foreach(p)




//	val thing = json.convertTo[Thing[Listing[Link]]]
//	for (x <- thing.data.children) {
//		println("%s url: %s".format(x.data.id, x.data.url))
//	}


//
//	val json = Await.result(pipeline(Get("/comments/1632du.json?limit=2&depth=2&sort=top")), 10 seconds).entity.asString.asJson
//	println(json.prettyPrint)
//
//	val things = json.convertTo[List[Thing[_]]]
//	for (t <- things) {
//		println("THING")
//		println(t)
//	}

//	for {
//		thing <- things
//		if thing.kind == Kinds.comment
//		x <- thing.asInstanceOf[Thing[Comment]].data.children
//	} {
//		println("comment: %s".format(thing.data.toJson.prettyPrint))
//		println("  comment: %s".format(x.data.toJson.prettyPrint))
//	}



//	println(thing)
//
//	println("raw:::")
//	println(json.prettyPrint)


//

//
//  val h = system.actorOf(Props[HelloWorld])
//	import akka.pattern.ask
//	for (result <- h ? "Landon") println(result)


  Thread.sleep(1000)
  system.shutdown
}

//class HelloWorld extends Actor {
//  protected def receive = {
//    case name:String => sender ! "Hello, %s".format(name)
//  }
//}
