## reddhead

Reddhead provides a Scala abstraction over Reddit web services. Reddhead makes it simple to write programs that consume data from the Reddit front page and subreddits, including article comments and other metadata.

Brainstem uses [Spray]() and [Akka](). Akka provides asynchronous messaging, and Spray provides a REST client built on top of Akka. [TimerBasedThrottler]() is used to throttle web service calls [in accordance with Reddit's rules]().

Model classes provide typed abstractions over Reddit JSON objects. See `model.scala`.

API's and use cases methods are also provided. See `api.scala`.

## getting started

Clone this git repo.



## use cases

### iterate front page links

``` scala

```

### download top images from r/pics

``` scala
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

``` scala
traverse("16716l") { (ancestors, comment) =>
	println("-" * ancestors.size + comment.body)
	ancestors.size < 4
}
```

### concurrently read 2 feeds ###

Thanks to Akka and Spray all web service interaction happens in background threads, and all API calls are non-blocking. This allows us to run concurrent behaviors.

``` scala
links(Query("/top"), new Listing(_)) { l =>
	println("TOP: " + l.name)
	true
}
links(Query("/new"), new Listing(_)) { l =>
	println("NEW: " + l.name)
	true
}
```

This example crawls links from two different feeds. Each link is printed to stdout, which will result in interleaved output. An improvement might be to write the output to files.

### monitor new stream

Realtime monitoring of new links (or other things).

``` scala
// register a function callback to be notified of new posts to /r/pics
```

### build social graph

Build a social graph representing the connections of Reddit users. Determine connections by publicly available data, such as comment replies.

``` scala
// with a bit more tooling this use case can be
// illistrated in a few lines of code
```

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