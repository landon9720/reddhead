package kuhn

import org.slf4j.LoggerFactory
import spray.json._
import DefaultJsonProtocol._

package object reddit {

  private val logger = LoggerFactory.getLogger("reddit")

  import logger._

  private def tryFormat[T](jsonFormat: JsonFormat[T]) = new JsonFormat[T] {
    def read(json: JsValue) = try {
      jsonFormat.read(json)
    } catch {
      case ex: Exception ⇒
        error(s"exception parsing JSON\n${json.prettyPrint}")
        throw ex
    }

    def write(obj: T) = ???
  }

  implicit val moreFormat                         = jsonFormat5(More)
  implicit val commentFormat: JsonFormat[Comment] = lazyFormat(jsonFormat6(Comment))
  implicit val linkFormat                         = jsonFormat10(Link)

  implicit val childFormat = new JsonFormat[Child] {
    def read(json: JsValue) = {
      val obj = json.asJsObject
      val kind = obj.fields("kind").asInstanceOf[JsString]
      val data = obj.fields("data").asInstanceOf[JsObject]
      Child(kind.value, kind.value match {
        case "t1" ⇒ commentFormat.read(data)
        case "t3" ⇒ linkFormat.read(data)
        case "more" ⇒ moreFormat.read(data)
      })
    }

    def write(obj: Child) = ???
  }

  implicit val dataFormat    = jsonFormat3(ListingData)
  implicit val listingFormat = jsonFormat2(Listing)

}
