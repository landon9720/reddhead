import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.map.ObjectMapper

package object kuhn {
	val mapper = new ObjectMapper

	implicit class rich_string(s:String) {
		def tab = s.split("\n").mkString("  ", "\n  ", "")
		def toJson = mapper.readTree(s)
		def optional = Option(s)
	}

	implicit class rich_json(j:JsonNode) {
		def pretty = mapper.writerWithDefaultPrettyPrinter.writeValueAsString(j)
	}
}
