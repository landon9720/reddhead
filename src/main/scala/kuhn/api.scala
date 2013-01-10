package kuhn

import org.codehaus.jackson.JsonNode
import spray.client.HttpConduit._
import concurrent._
import concurrent.duration._

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

	def user(u:String) = user_overview(u)
	def user_overview(u:String) = Query("user/%s".format(u))
	def user_comments(u:String) = Query("user/%s/comments".format(u))
	def user_submitted(u:String) = Query("user/%s/submitted".format(u))

	case class Query(path:String, before:Option[String] = None, after:Option[String] = None) {
		def previous(listing:Listing):Option[Query] =
			for (before <- listing.before) yield copy(before = listing.before, after = None)
		def next(listing:Listing):Option[Query] =
			for (after <- listing.after) yield copy(before = None, after = listing.after)
		val url = "/%s.json?".format(path) +
			before.map("before=%s&".format(_)).getOrElse("") +
			after.map("after=%s&".format(_)).getOrElse("")
	}

	def request(q:Query) = {
		pipeline(Get(q.url) ~> addHeader("User-Agent", "https://github.com/landon9720/reddhead"))
	}

	def scroll(q:Query, factory:JsonNode=>Listing = new Listing(_))(f:PartialFunction[Thing, Boolean]) {
		for (httpResponse <- request(q)) {
			val listing = factory(httpResponse.entity.asString.toJson)
			val continue = (for (t <- listing if f.isDefinedAt(t)) yield t) forall(f(_))
			if (continue) q.next(listing).map(next => scroll(next, factory)(f))
		}
	}

	def monitor(q:Query, factory:JsonNode=>Listing = new Listing(_), previous_names:Set[String] = Set.empty)(f:PartialFunction[Thing, Unit]) {
		val deadline = 30 seconds fromNow
		val names = collection.mutable.Set[String]()
		scroll(q, factory) {
			case _ if deadline.isOverdue() => {
				monitor(q, factory, previous_names ++ names)(f)
				false
			}
			case t if f.isDefinedAt(t) => {
				if (!names.contains(t.name)) {
					names += t.name
					f(t)
				}
				true
			}
		}
	}

	def links(q:Query)(f:PartialFunction[Link, Unit]) {
		scroll(q) {
			case l:Link if f.isDefinedAt(l) => f(l); true
		}
	}

	def first_link(q:Query)(f:Link=>Unit) {
		scroll(q) {
			case l:Link => f(l); false
		}
	}

	def monitor_links(q:Query)(f:PartialFunction[Link, Unit]) {
		monitor(q) {
			case l:Link if f.isDefinedAt(l) => f(l)
		}
	}

	def comments(linkId:String)(f:PartialFunction[(Seq[Comment], Comment), Unit]) {
		scroll(Query("comments/%s".format(linkId)), j => new Listing(j.get(1))) {
			case c:Comment => traverse_comments(c)(f); true
			case _:More => true
		}
	}

	def first_comment(linkId:String)(f:(Comment) => Unit) {
		scroll(Query("comments/%s".format(linkId)), j => new Listing(j.get(1))) {
			case c:Comment => f(c); false
			case _:More => true
		}
	}

	def monitor_comments(linkId:String)(f:PartialFunction[(Seq[Comment], Comment), Unit]) {
		monitor(Query("comments/%s".format(linkId)), j => new Listing(j.get(1))) {
			case c:Comment => traverse_comments(c)(f)
			case _:More =>
		}
	}

	def traverse_comments(c:Comment, ancestors:Seq[Comment] = Seq())(f:PartialFunction[(Seq[Comment], Comment), Unit]) {
		if (f.isDefinedAt((ancestors, c))) f((ancestors, c))
		c.replies.foreach {
			case child:Comment => traverse_comments(child, c +: ancestors)(f)
			case _:More =>
		}
	}

//	def monitor_links(q:Query)(f:PartialFunction[Link, Unit]) {
//		val last = collection.mutable.Set[String]
//		system.scheduler.schedule(0 seconds, 30 seconds) {
//			links(q) { case l => f(l) }
//		}
//	}

	def shutdown = system.shutdown
}
