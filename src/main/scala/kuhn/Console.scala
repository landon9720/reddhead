package kuhn

object Console extends App {

	import system._
	import api._

	// print front page image links
	links(frontpage) {
		case link if link.domain == "i.imgur.com" => println("""<img href="%s"/>""".format(link))
	}

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
//	first_link(frontpage) {
//		l: Link => monitor_comments(l) {
//			case (_, c) => println(c)
//		}
//	}

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
//	comments("92dd8") {
//		case (ancestors, c) => println("-" * ancestors.size + c.body)
//	}

	// build a social graph by reading comments
//	var connections = Map[Set[String], Int]().withDefaultValue(0)
//	links(frontpage_top) {
//		case link => comments(link) {
//			case (ancestors, comment) => {
//				val user1 = ancestors match {
//					case parent :: _ => parent.author
//					case Nil => link.author
//				}
//				val user2 = comment.author
//				val key = Set(user1, user2)
//				connections = connections + (key -> (connections(key) + 1))
//				println("%s / %s = %d".format(user1, user2, connections(key)))
//			}
//		}
//	}

	println("SLEEP...")
	Thread.sleep(60000)
	shutdown
}