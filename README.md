## brainstem

Brainstem provides a Scala abstraction over Reddit web services. Brainstem makes it simple to write programs that consume data from the Reddit front page and subreddits, including article comments and other metadata.

Brainstem uses [Spray]() and [Akka](). Akka provides asynchronous messaging logic, and Spray provides a REST client built on top of Akka. [TimerBasedThrottler]() is used to throttle web service calls [in accordance with Reddit's rules]().

Model classes provide typed abstractions over Reddit JSON objects. API's and use cases methods are also provided.

## getting started

Clone this git repo.

This project is not currently packaged as a library.

## use cases

### iterate front page links

```

```

### download top images from r/pics

```
scroll(Subreddit("pics").top("YEAR").copy(limit = 100), pages = 10) { link:Link =>
	system.actorOf(Props(new Actor with ActorLogging {
		def receive = {
			case cmd:String => {
				log.info(cmd)
				import sys.process._
				cmd.run
			}
		}
	})) ! "wget -o /tmp/wget.log -P /tmp %s".format(link.url)
}
```

### crawl link comments

```
traverse("16716l") { (ancestors, comment) =>
	println("-" * ancestors.size + comment.body)
	ancestors.size < 4
}
```

### concurrently read 2 feeds ###

``` scala
scroll(Query("/top"), new Listing(_)) { t =>
	println("TOP: " + t.name)
	true
}
scroll(Query("/new"), new Listing(_)) { t =>
	println("NEW: " + t.name)
	true
}
```

### monitor new stream

## references

* https://github.com/reddit/reddit/blob/master/r2/r2/controllers/api.py
* http://www.reddit.com/dev/api
* https://github.com/reddit/reddit/wiki/JSON
* http://jersey.java.net/nonav/documentation/latest/client-api.html
* https://bitbucket.org/_oe/jreddit
* http://dispatch.databinder.net/Dispatch.html
* http://nurkiewicz.blogspot.com/2012/11/non-blocking-io-discovering-akka.html
* http://doc.akka.io/docs/akka/snapshot/contrib/throttle.html
* http://spray.io/documentation/spray-client/
* https://github.com/spray/spray-json