package kuhn

import org.codehaus.jackson.JsonNode

// this implementation is based on the documentation:
// https://github.com/reddit/reddit/wiki/JSON
// and reverse engineering Reddit's live web service

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

class Listing(val json:JsonNode) extends Thing with Iterable[Thing] {

	val kind = "Listing"
	val id = "none"
	val name = "none"
	val before = json.path("data").path("before").getTextValue.optional
	val after = json.path("data").path("after").getTextValue.optional

	import collection.JavaConversions._
	val things = for (thing <- json.path("data").path("children")) yield Thing(thing)
	def iterator = things.iterator

	override def toString =
		"listing (before=%s after=%s):\n".format(before.getOrElse("null"), after.getOrElse("null"))
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

	override def toString = body.split("\n").head
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

	override def toString = url
}