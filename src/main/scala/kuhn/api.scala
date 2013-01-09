package kuhn

import org.codehaus.jackson.JsonNode
import spray.client.HttpConduit._
import scala.Some
import akka.actor.{Props, ActorSystem}
import spray.can.client.HttpClient
import spray.client.HttpConduit
import akka.contrib.throttle.TimerBasedThrottler
import akka.contrib.throttle.Throttler.{SetTarget, Rate}
import spray.io.IOExtension
import concurrent.duration._

object api {

	implicit val system = ActorSystem()
	import system.dispatcher

	val ioBridge = IOExtension(system).ioBridge()
	val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))

	val conduit = system.actorOf(Props(new HttpConduit(httpClient, "www.reddit.com", 80)))

	val throttler = system.actorOf(Props(new TimerBasedThrottler(Rate(1, 3 seconds))))
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
		                //        limit:Int = 2,
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

	def scroll(q:Query, factory:JsonNode=>Listing = new Listing(_))(f:Thing=>Boolean) {
		for (httpResponse <- pipeline(Get(q.url))) {
			val listing = factory(httpResponse.entity.asString.toJson)
			val continue = listing.things.forall(f)
			if (continue) q.next(listing).map(next => scroll(next, factory)(f))
		}
	}

	def links(q:Query, factory:JsonNode=>Listing = new Listing(_))(f:Link=>Boolean) {
		scroll(q, factory) {
			case l:Link => f(l)
		}
	}

	def comments(linkId:String)(f:(Seq[Comment], Comment)=>Boolean) {
		def impl(c:Comment, ancestors:Seq[Comment] = Seq()):Boolean = {
			f(ancestors, c) && c.replies.things.forall {
				case child:Comment => impl(child, c +: ancestors)
				case _:More => true
			}
		}
		scroll(Query("comments/%s".format(linkId)), j => new Listing(j.get(1))) {
			case c:Comment => impl(c); true
			case _:More => true
		}
	}

	def shutdown = system.shutdown
}
