//package kuhn
////
//object Kinds {
//	val comment = "t1"
//	val account = "t2"
//	val link = "t3"
//	val message = "t4"
//	val subreddit = "t5"
//	val Listing = "Listing"
//}
////
//////https://github.com/reddit/reddit/wiki/JSON
//////
//////This document describes the data structure of an object returned when using the reddit JSON API.  Items attached with a `?` are not definite and are subject to change.  Validation and peer review are needed for those items.  They should not be taken as literal question marks.
//////
//////## `thing` (reddit base class) ##
//////All `thing`s have these attributes:
//////
//////| **type**  | **name**                 | **description** |
//////|:----------|:-------------------------|:----------------|
//////| `String`  | `id`                     | this item's identifier, e.g. "8xwlg" |
//////| `String`  | `name`                   | Fullname of comment, e.g. "t1_c3v7f8u" |
//////| `String`  | `kind`                   | All `thing`s have a `kind`.  The kind is a String identifier that denotes the object's type.  Some examples: `Listing`, `more`, `t1`, `t2` |
//////| `Object`  | `data`                   | A custom data structure used to hold valuable information.  This object's format will follow the data structure respective of its kind.  See below for specific structures. |
//////
//////**Exception**: Listing `thing`s have neither `name` nor `id` because they are indefinite objects.  That is, they are system generated, not user submitted, and are subject to change quickly and expire.
////
////trait Thing {
////	def id:String
////	def name:String
////	def kind:String
////	def data:Any
////}
//
//trait Thing[T] {
//	def kind:String
//	def data:T
//}
//
////	                   val kind:String,
////	                   val data:T
////	                   ) {
//	//		override def toString = "thing " + kind + ": " + data match {
//	//			case js:JsValue => js.prettyPrint
//	//			case x => x.toString
//	//		}
//
//
////
//////
//////## `listing` ##
//////Used to [paginate](http://en.wikipedia.org/wiki/Pagination) content that is too long to display in one go.  Add the query argument `before` or `after` with the value given to get the previous or next page.  This is usually used in conjunction with a `count` argument.
//////
//////**Exception**:  Unlike the other classes documented on this page, `Listing` is [not a `thing` subclass](https://github.com/reddit/reddit/blob/master/r2/r2/models/listing.py#L36), as it inherits directly from the Python base class, Object.  Although it does have `data` and `kind` as parameters, it does not have `id` and `name`.  A listing's `kind` will always be `Listing` and its data will be a List of `thing`s.
//////
//////| **type**  | **name**                 | **description** |
//////|:----------|:-------------------------|:----------------|
//////| `String`  | `before`                 | The fullname of the listing that follows before this page.  `null` if there is no previous page. |
//////| `String`  | `after`                  | The fullname of the listing that follows after this page. `null` if there is no next page. |
//////| `String`  | `modhash`                | This modhash is not the same modhash provided upon login.  You do not need to update your user's modhash everytime you get a new modhash.  You can reuse the modhash given upon login. |
//////| `List<thing>`|  `data`               | A list of `thing`s that this Listing wraps. |
//////
////
//
//case class Listing(
//	val children:Option[List[Thing[_]]],
//	val before:Option[String],
//	val after:Option[String]
//) extends Thing[List[Thing[_]]] {
//	def kind = "Listing"
//	def data = children.getOrElse(List())
//}
//
////class Listing(
////	val before:String,
////	val after:String,
////	val modhash:String,
////	val data:List[Thing]
////)
////
//////## votable (implementation) ##
//////All `thing`s that implement `votable` have these attributes:
//////
//////| **type**  | **name**                 | **description** |
//////|:----------|:-------------------------|:----------------|
//////| `int`     | `ups`                    | the number of upvotes.  does this include one's own upvote? |
//////| `int`     | `downs`                  | the number of downvote.  does this include one's own downvote? |
//////| `Boolean` | `likes`                  | true if thing is liked by the user.  false if thing is disliked.  null if the user is neutral on the thing.  Certain languages such as Java may need to use a boolean wrapper that supports null assignment.|
//////
////
////trait Votable {
////	def ups:Int
////	def downs:Int
////	def likes:Option[Boolean]
////}
////
//////## created (implementation) #
//////All `thing`s that implement `created` have these attributes:
//////
//////| **type**  | **name**                 | **description** |
//////|:----------|:-------------------------|:----------------|
//////| `long`?   | `created`                | the localized time of creation. ex: "1331042771.0" |
//////| `long`?   | `created_utc`            | the UTC time this item was created since the unix start time in seconds. "1331017571.0"|
//////
////
////trait Created {
////	def created:Long
////	def created_utc:Long
////}
////
//////
//////## Data Structures ##
//////***
//////### comment (implements votable | created) ###
//////| **type**  | **name**                 | **description** |
//////|:----------|:-------------------------|:----------------|
//////| `String`  | `author`                 | the account name of the poster |
//////| `String`  | `author_flair_css_class` | the css class of the author's flair.  subreddit specific |
//////| `String`  | `author_flair_text`      | the text of the author's flair.  subreddit specific |
//////| `String`  | `body`                   | the raw text.  this is the unformatted text which includes the raw markup characters such as `**` for bold. |
//////| `String`  | `body_html`              | the formatted html text.  this is the html formatted version of the marked up text.  Items that are boldened by `**` or `***` will now have `<em>` or `***` tags on them. Additionally, bullets and numbered lists will now be in html list format. ***NOTE:*** The html string will be escaped.  You must unescape to get the raw html.|
//////| `String`  | `link_id`                |  |
//////| `String`  | `parent_id`              |  |
//////| `String`  | `subreddit`              | subreddit of thing excluding the /r/ prefix. "pics" |
//////| `String`  | `subreddit_id`           |  |
//////
//
//case class Comment(
////	val id:String,
////	val name:String,
////	val kind:String,
//	val data:Listing,
//	val ups:Int,
//	val downs:Int,
//	val likes:Option[Boolean],
//	val created:Long,
//	val created_utc:Long,
//	val author:String,
//	val author_flair_css_class:String,
//	val author_flair_text:String,
//	val body:String,
//	val body_html:String,
//	val link_id:String,
//	val parent_id:String,
//	val subreddit:String,
//	val subreddit_id:String
//) extends Thing[Listing] {
//	def kind = Kinds.comment
//}
//
////
//////***
//////### link (implements votable | created) ###
//////| **type**  | **name**                 | **description** |
//////|:----------|:-------------------------|:----------------|
//////| `String`  | `author`                 | the account name of the poster |
//////| `String`  | `author_flair_css_class` | the css class of the author's flair.  subreddit specific |
//////| `String`  | `author_flair_text`      | the text of the author's flair.  subreddit specific |
//////| `boolean` | `clicked`                | probably always returns false |
//////| `String`  | `domain`                 | the domain of this link.  Self posts will be `self.reddit.com` while other examples include `en.wikipedia.org` and `s3.amazon.com` |
//////| `boolean` | `hidden`                 | true if the post is hidden by the logged in user.  false if not logged in or not hidden. |
//////| `boolean` | `is_self`                | true if this link is a selfpost |
//////| `Object`  | `media`                  | unknown.  I need someone else to document this as I haven't done much research into this |
//////| `Object`  | `media_embed`            | unknown.  I need someone else to document this as I haven't done much research into this |
//////| `int`     | `num_comments`           | the number of comments that belong to this link |
//////| `boolean` | `over_18`                | true if the post is tagged as NSFW.  False if otherwise |
//////| `String`  | `permalink`              | relative url of the permanent link for this link |
//////| `boolean` | `saved`                  | true if this post is saved by the logged in user |
//////| `int`     | `score`                  | the net-score of the link.  **note:** A submission's score is simply the number of upvotes minus the number of downvotes. If five users like the submission and three users don't it will have a score of 2. Please note that the vote numbers are not "real" numbers, they have been "fuzzed" to prevent spam bots etc. So taking the above example, if five users upvoted the submission, and three users downvote it, the upvote/downvote numbers may say 23 upvotes and 21 downvotes, or 12 upvotes, and 10 downvotes. The points score is correct, but the vote totals are "fuzzed".|
//////| `String`  | `selftext`               | the raw text.  this is the unformatted text which includes the raw markup characters such as `**` for bold. |
//////| `String`  | `selftext_html`          | the formatted escaped html text.  this is the html formatted version of the marked up text.  Items that are boldened by `**` or `***` will now have `<em>` or `***` tags on them. Additionally, bullets and numbered lists will now be in html list format. ***NOTE:*** The html string will be escaped.  You must unescape to get the raw html.|
//////| `String`  | `subreddit`              |  |
//////| `String`  | `subreddit_id`           |  |
//////| `String`  | `thumbnail`              | full url to the thumbnail for this link |
//////| `String`  | `title`                  |  |
//////| `String`  | `url`                    | the link of this post.  the permalink if this is a self-post |
//////| `long`    | `edited`                 | Indicates if link has been edited. Will be the edit timestamp if the link has been edited and return false otherwise. https://github.com/reddit/reddit/issues/581 |
//////
//
//case class Link(
//	               id:String,
//	               name:String,
//	               //	val kind:String,
//	               //	val data:Any,
//	               ups:Int,
//	               downs:Int,
//	               //	val likes:Option[Boolean],
//	               //	val created:Long,
//	               created_utc:Long,
//	               author:String,
//	               //	val author_flair_css_class:String,
//	               //	val author_flair_text:String,
//	               //	val clicked:Boolean,
//	               domain:String,
//	               //	val hidden:Boolean,
//	               is_self:Boolean,
//	               ////	media:Any,
//	               ////	media_embed:Any,
//	               num_comments:Int,
//	               over_18:Boolean,
//	               permalink:String,
//	               //	val saved:Boolean,
//	               //		score:Int,
//	               selftext:String,
//	               //			val selftext_html:String,
//	               subreddit:String,
//	               //			val subreddit_id:String,
//	               //	val thumbnail:String,
//	               title:String,
//	               url:String
//	               //	val edited:Long
//	               ) extends Thing[Unit] {
//	def kind = Kinds.link
//	def data = Unit
//}
//
////
////class Link(
////	val id:String,
////	val name:String,
////	val kind:String,
////	val data:Any,
////	val ups:Int,
////	val downs:Int,
////	val likes:Option[Boolean],
////	val created:Long,
////	val created_utc:Long,
////	val author:String,
////	val author_flair_css_class:String,
////	val author_flair_text:String,
////	val clicked:Boolean,
////	val domain:String,
////	val hidden:Boolean,
////	val is_self:Boolean,
//////	media:Any,
//////	media_embed:Any,
////	val num_comments:Int,
////	val over_18:Boolean,
////	val permalink:String,
////	val saved:Boolean,
////	val score:Int,
////	val selftext:String,
////	val selftext_html:String,
////	val subreddit:String,
////	val subreddit_id:String,
////	val thumbnail:String,
////	val title:String,
////	val url:String,
////	val edited:Long
////) extends Thing with Votable with Created
////
//////***
//////### subreddit ###
//////| **type**  | **name**                 | **description** |
//////|:----------|:-------------------------|:----------------|
//////| `String`  | `description`            |  |
//////| `String`  | `display_name`           |  |
//////| `boolean` | `over18`                 | is_nsfw? |
//////| `long`    | `subscribers`            | the number of redditors subscribed to this subreddit |
//////| `String`  | `title`                  |  |
//////| `String`  | `url`                    | The relative URL of the subreddit.  Ex: "/r/pics/" |
//////
////
////class Subreddit(
////	val id:String,
////	val name:String,
////	val kind:String,
////	val data:Any,
////	val description:String,
////	val display_name:String,
////	val over18:Boolean,
////	val subscribers:Long,
////	val title:String,
////	val url:String
////) extends Thing
////
//////***
//////### message (implements created) ###
//////| **type**  | **name**                 | **description** |
//////|:----------|:-------------------------|:----------------|
//////| `String`  | `author`                 |  |
//////| `String`  | `body`                   |  |
//////| `String`  | `body_html`              |  |
//////| `String`  | `context`                | does not seem to return null but an empty string instead. |
//////| `Message`?| `first_message`          |  |
//////| `String`  | `name`                   | ex: "t4_8xwlg" |
//////| `boolean` | `new`                    | unread?  not sure |
//////| `String`  | `parent_id`              | null if no parent is attached |
//////| `String`  | `replies`                | Again, an empty string if there are no replies. |
//////| `String`  | `subject`                | subject of message |
//////| `String`  | `subreddit`              | null if not a comment. |
//////| `boolean` | `was_comment`            |  |
//////
////
////class Message(
////	val id:String,
////	val name:String,
////	val kind:String,
////	val data:Any,
////	val created:Long,
////	val created_utc:Long,
////	val comment_karma:Int,
////	//	created:Long,
////	val created_urc:Long,
////	val has_mail:Boolean,
////	val has_mod_mail:Boolean,
////	//	id:String,
////	val is_gold:Boolean,
////	val is_mod:Boolean,
////	val link_karma:Int,
////	val modhash:String
////	//	name:String
////) extends Thing with Created
////
//////***
//////### account ###
//////| **type**       | **name**                 | **description** |
//////|:---------------|:-------------------------|:----------------|
//////| `int`          | `comment_karma`          |  |
//////| `long`         | `created`                |  |
//////| `long`         | `created_utc`            |  |
//////| `boolean`      | `has_mail`               | does the user have unread mail? |
//////| `boolean`      | `has_mod_mail`           |  |
//////| `String`       | `id`                     |  |
//////| `boolean`      | `is_gold`                |  |
//////| `boolean`      | `is_mod`                 |  |
//////| `int`          | `link_karma`             |  |
//////| `String`       | `modhash`                |  |
//////| `String`       | `name`                   | The username of the account in question.  This attribute overrides the superclass's `name` attribute.  Do not confuse an account's `name` which is the account's username with a thing's `name` which is the thing's FULLNAME.  See [API: Glossary](API) for details on what FULLNAMEs are. |
//////
////
////class Account(
////	val id:String,
////	val name:String,
////	val kind:String,
////	val data:Any,
////	val comment_karma:Int,
////	val created:Long,
////	val created_utc:Long,
////	val has_mail:Boolean,
////	val has_mod_mail:Boolean,
////	//id:String,
////	val is_gold:Boolean,
////	val is_mod:Boolean,
////	val link_karma:Int,
////	val modhash:String
////	//name:String
////) extends Thing with Created
////
//////Example:
//////```Raw account data
//////{	"kind": "t2",
//////"data": {
//////"has_mail": false,
//////"name": "fooBar",
//////"created": 123456789.0,
//////"modhash": "f0f0f0f0f0f0f0f0...",
//////"created_utc": 1315269998.0,
//////"link_karma": 31,
//////"comment_karma": 557,
//////"is_gold": false,
//////"is_mod": false,
//////"id": "5sryd",
//////"has_mod_mail": false
//////}
//////}
//////```
//////
//////***
//////### more
//////| **type**       | **name**                 | **description** |
//////|:---------------|:-------------------------|:----------------|
//////| `List<String>` | `children`               | A list of String `id`s that are the additional `thing`s that can be downloaded but are not because there are too many to list. |
//////
//////
//////Example:
//////```Json example of more
//////{
//////"kind": "more",
//////"data": {
//////"children": [
//////"c3y9tyh",
//////"c3y9ul7",
//////"c3y9unp",
//////"c3y9uoi"
//////],
//////"name": "t1_c3y9tyh",
//////"id": "c3y9tyh"
//////}
//////}
//////```