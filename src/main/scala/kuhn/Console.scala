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
	val result = pipeline(Get("/top/.json?limit=10"))

	val r = Await.result(result, 10 seconds)

	val json = r.entity.asString.asJson


	object RedditJsonProtocol extends DefaultJsonProtocol {
		implicit def thingFormat[A :JsonFormat] = jsonFormat2(Thing.apply[A])
		implicit def listingFormat[A :JsonFormat] = jsonFormat3(Listing.apply[A])
		implicit val linkFormat = jsonFormat15(Link.apply)
	}

	import RedditJsonProtocol._

	case class Thing[T](
		val kind:String,
		val data:T
	) {
//		override def toString = "thing " + kind + ": " + data match {
//			case js:JsValue => js.prettyPrint
//			case x => x.toString
//		}
	}

	case class Listing[T](
		val children:List[Thing[T]],
		val before:Option[String],
		val after:Option[String]
	)

	case class Link(
		id:String,
		name:String,
		//	val kind:String,
		//	val data:Any,
		ups:Int,
		downs:Int,
		//	val likes:Option[Boolean],
		//	val created:Long,
		created_utc:Long,
		author:String,
		//	val author_flair_css_class:String,
		//	val author_flair_text:String,
		//	val clicked:Boolean,
		domain:String,
		//	val hidden:Boolean,
		is_self:Boolean,
		////	media:Any,
		////	media_embed:Any,
		num_comments:Int,
		over_18:Boolean,
		permalink:String,
		//	val saved:Boolean,
//		score:Int,
		selftext:String,
		//			val selftext_html:String,
		subreddit:String,
		//			val subreddit_id:String,
		//	val thumbnail:String,
		title:String,
		url:String
		//	val edited:Long
	)

	val thing = json.convertTo[Thing[Listing[Link]]]

	for (x <- thing.data.children) {
		println("title: %s url: %s ups: %d downs: %d".format(x.data.title, x.data.url, x.data.ups, x.data.downs))
	}

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
