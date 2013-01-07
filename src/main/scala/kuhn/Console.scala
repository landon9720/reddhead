package kuhn

import spray.can.client.HttpClient
import spray.io._



import akka.actor.{Props, ActorSystem}
import spray.client.HttpConduit
import HttpConduit._
import akka.dispatch.Await

import akka.util.duration._

import spray.json._

object Console extends App {

//		import akka.util.duration._
//		import akka.util.Timeout
//		implicit val timeout:Timeout = 10.seconds

	implicit val system = ActorSystem()
	val ioBridge = IOExtension(system).ioBridge()
	val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))
	val conduit = system.actorOf(Props(new HttpConduit(httpClient, "www.reddit.com", 80)))


	val pipeline = sendReceive(conduit)
	val result = pipeline(Get("/top/.json?limit=2"))

	val r = Await.result(result, 10 seconds)

	val json = r.entity.asString.asJson


	object RedditJsonProtocol extends DefaultJsonProtocol {

		implicit object LinkJsonFormat extends RootJsonFormat[Link] {
			def write(obj:Link) = null

			def read(json:JsValue) = new Link(
				id = json.asJsObject.fields.get("before").collect { case JsString(x) => x } getOrElse(""),
		        name = json.asJsObject.fields.get("name").collect { case JsString(x) => x } getOrElse(""),
				kind = json.asJsObject.fields.get("kind").collect { case JsString(x) => x } getOrElse(""),
				data = "",
				ups = json.asJsObject.fields.get("ups").collect { case JsNumber(x) => x.toInt } getOrElse("0"),
				downs = json.asJsObject.fields.get("downs").collect { case JsNumber(x) => x.toInt } getOrElse("0"),

			val ups:Int,
			val downs:Int,
			val likes:Option[Boolean],
			val created:Long,
			val created_utc:Long,
			val author:String,
			val author_flair_css_class:String,
			val author_flair_text:String,
			val clicked:Boolean,
			val domain:String,
			val hidden:Boolean,
			val is_self:Boolean,
			//	media:Any,
			//	media_embed:Any,
			val num_comments:Int,
			val over_18:Boolean,
			val permalink:String,
			val saved:Boolean,
			val score:Int,
			val selftext:String,
			val selftext_html:String,
			val subreddit:String,
			val subreddit_id:String,
			val thumbnail:String,
			val title:String,
			val url:String,
			val edited:Long
			)
		}

		implicit object ListingJsonFormat extends RootJsonFormat[Listing] {
			def write(l:Listing) = JsObject(
				"before" -> JsString(l.before),
				"after" -> JsString(l.after),
				"modhash" -> JsString(l.modhash)
//				"data" -> JsObject(l.data)
			)

			def read(value: JsValue) = new Listing(
				before = value.asJsObject.fields.get("before").map(_.toString).getOrElse(""),
				after = value.asJsObject.fields.get("after").map(_.toString).getOrElse(""),
				modhash = value.asJsObject.fields.get("modhash").map(_.toString).getOrElse(""),
				data = for {
					child <- json.asJsObject.fields("data").asJsObject.fields("children").asInstanceOf[JsArray].elements
				} yield child.convertTo[Link]
			)

//				value.asJsObject.getFields("kind", "before", "after", "modhash") match {
//				case Seq(JsString(before), JsString(after), JsString(modhash)) =>
//					new Listing[Unit](before, after, modhash, List())
//				case x => println(x.toString); sys.error("foo")
//			}
		}


	}

	import RedditJsonProtocol._

	val listing = json.asJsObject.fields("data").asJsObject.fields("children") match {
		case values:JsArray => for (v <- values.elements) {
			println(v.prettyPrint)
		}
	}
//	println(listing)


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
