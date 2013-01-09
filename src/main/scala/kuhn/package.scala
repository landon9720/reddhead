import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.map.ObjectMapper

package object kuhn {
	val mapper = new ObjectMapper

	implicit class rich_string(s:String) {
		def toJson = mapper.readTree(s)
		def optional = Option(s)
	}

	implicit class rich_json(j:JsonNode) {
		def pretty = mapper.writerWithDefaultPrettyPrinter.writeValueAsString(j)
	}

	implicit def thing_to_thing_id(t:Thing):String = t.id
}
