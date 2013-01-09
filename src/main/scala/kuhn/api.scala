package kuhn

import org.codehaus.jackson.JsonNode
import spray.client.HttpConduit._

// abstraction over Reddit's REST API, documented here:
// http://www.reddit.com/dev/api

object api {

	import system._
	import system.dispatcher

	def frontpage = frontpage_hot
	def frontpage_hot = Query("")
	def frontpage_new = Query("/new")
	def frontpage_controversial = Query("/controversial")
	def frontpage_top = Query("/top")

	def subreddit(r:String) = subreddit_hot(r)
	def subreddit_hot(r:String) = Query("r/%s".format(r))
	def subreddit_new(r:String) = Query("r/%s/new".format(r))
	def subreddit_controversial(r:String) = Query("r/%s/controversial".format(r))
	def subreddit_top(r:String) = Query("r/%s/top".format(r))

	case class Query(path:String, before:Option[String] = None, after:Option[String] = None) {
		def previous(listing:Listing):Option[Query] =
			for (before <- listing.before) yield copy(before = listing.before, after = None)
		def next(listing:Listing):Option[Query] =
			for (after <- listing.after) yield copy(before = None, after = listing.after)
		val url:String = "/%s.json?".format(path) +
			before.map("before=%s&".format(_)).getOrElse("") +
			after.map("after=%s&".format(_)).getOrElse("")
	}

	def scroll(q:Query, factory:JsonNode=>Listing = new Listing(_))(f:PartialFunction[Thing, Unit]) {
		for (httpResponse <- pipeline(Get(q.url))) {
			val listing = factory(httpResponse.entity.asString.toJson)
			for (t <- listing if f.isDefinedAt(t)) f(t)
			q.next(listing).map(next => scroll(next, factory)(f))
		}
	}

	def links(q:Query, factory:JsonNode=>Listing = new Listing(_))(f:PartialFunction[Link, Unit]) {
		scroll(q, factory) {
			case l:Link if f.isDefinedAt(l) => f(l)
		}
	}

	def comments(linkId:String)(f:PartialFunction[(Seq[Comment], Comment), Unit]) {
		def impl(c:Comment, ancestors:Seq[Comment] = Seq()) {
			if (f.isDefinedAt((ancestors, c))) f((ancestors, c))
			c.replies.foreach {
				case child:Comment => impl(child, c +: ancestors)
				case _:More =>
			}
		}
		scroll(Query("comments/%s".format(linkId)), j => new Listing(j.get(1))) {
			case c:Comment => impl(c)
			case _:More =>
		}
	}

	def shutdown = system.shutdown
}
