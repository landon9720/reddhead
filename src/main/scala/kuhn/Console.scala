package kuhn

import api._

object Console extends App {


	// iterate front page links
	links(Query("/")) { l:Link =>
		println("""<img href="%s"/>""".format(l.url))
		true
	}

//	// concurrently read 2 different feeds
//	scroll(Query("/top")) { t =>
//		println("TOP: " + t.name)
//		true
//	}
//	scroll(Query("/new")) { t =>
//		println("NEW: " + t.name)
//		true
//	}

	// download top images from r/pics
//	import sys.process._
//	val executeCommand = system.actorOf(Props(new Actor {
//		def receive = {
//			case cmd:String => cmd.run
//		}
//	}))
//	scroll(Subreddit("pics").top("year")) { link:Link =>
//		executeCommand ! "wget -o /tmp/wget.log -P /tmp %s".format(link.url)
//	}

	// iterate link comments
//	comments("16716l") { (ancestors, c) =>
//		println("-" * ancestors.size + c.body)
//		true
//	}

	println("SLEEP...")
	Thread.sleep(30000)
	api.shutdown
}