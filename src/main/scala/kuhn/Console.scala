package kuhn

object Console extends App {

	import system._
	import api._

	// print front page image links
//	links(frontpage) {
//		case link if link.domain == "i.imgur.com" => println("""<img href="%s"/>""".format(link))
//	}

	// print all submissions and comments by user
//	scroll(user("masta")) {
//		case l:Link => println("link: %s".format(l))
//		case c:Comment => println("comment: " + c)
//	}

	// monitor the front page for new posts
//	monitor_links(frontpage) {
//		case l => println(l)
//	}

	// monitor the top post for new comments
	first_link(frontpage) {
		l: Link => monitor_comments(l) {
			case (_, c) => println(c)
		}
	}

	// concurrently read 2 feeds
//	links(frontpage_top) {
//		case t => println("top: " + t)
//	}
//	links(frontpage_new) {
//		case t => println("new: " + t)
//	}

	// download images from r/pics
//	import sys.process._
//	import akka.actor._
//	val execute = system.actorOf(Props(new Actor {
//		def receive = {
//			case cmd:String => cmd.run
//		}
//	}))
//	links(subreddit("pics")) {
//		case link => execute ! "wget -o /tmp/wget.log -P /tmp %s".format(link)
//	}

	// crawl link comments
//	comments("16716l") {
//		case (ancestors, c) => println("-" * ancestors.size + c.body)
//	}

	println("SLEEP...")
	Thread.sleep(120000)
	shutdown
}