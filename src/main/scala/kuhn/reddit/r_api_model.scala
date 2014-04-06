package kuhn.reddit

case class Listing(
  kind: String,
  data: ListingData
  )

case class ListingData(
  children: List[Child],
  after: Option[String],
  before: Option[String]
  )

case class Child(
  kind: String,
  data: ChildData
  )

sealed trait ChildData

case class Link(
  name: String,
  id: String,
  domain: String,
  url: String,
  title: String,
  selftext: String,
  ups: Int,
  downs: Int,
  subreddit: String,
  author: String
  ) extends ChildData

case class Comment(
  name: String,
  id: String,
  body: String,
  ups: Int,
  downs: Int,
  replies: Option[Either[Listing, String]]
  ) extends ChildData

case class More(
  name: String,
  id: String,
  count: Int,
  parent_id: String,
  children: List[String]
  ) extends ChildData
