package kuhn

object Console extends App {

	import api._

	// read front page images
	links(frontpage) {
		case l:Link if l.domain == "i.imgur.com" => println("""<img href="%s"/>""".format(l.url))
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
	shutdown
}