## reddhead

Reddhead provides a Scala abstraction over Reddit web services. Reddhead makes it simple to write programs that consume data from the Reddit front page and subreddits, including article comments and other metadata. The intent is to provide an abstraction that is type safe and conforms to idiomatic functional programing style.

Reddhead uses [Spray]() and [Akka](). Akka provides asynchronous messaging, and Spray provides a REST client built on top of Akka. [TimerBasedThrottler]() is used to throttle web service calls [in accordance with Reddit's rules]().

Model classes provide typed abstractions over Reddit JSON objects. See [`model.scala`](https://github.com/landon9720/reddhead/blob/master/src/main/scala/kuhn/model.scala).

APIs provide abstractions over Reddit queries and use cases. See [`api.scala`](https://github.com/landon9720/reddhead/blob/master/src/main/scala/kuhn/api.scala).

The [`Console`](https://github.com/landon9720/reddhead/blob/master/src/main/scala/kuhn/Console.scala) class demonstrates how to create the environment and use the API.

## getting started

Clone this git repo.

Examine [`Console.scala`](https://github.com/landon9720/reddhead/blob/master/src/main/scala/kuhn/Console.scala). Uncomment the use case you want to test. Run with `sbt run`.

I have been having memory issues with SBT. I am experimenting with `export SBT_OPTS='-XX:MaxPermSize=512m -Xms512m -Xmx512m'`.

## use cases

### print front page image links

``` scala
links(frontpage) {
	case link if link.domain == "i.imgur.com" => println("""<img href="%s"/>""".format(link))
}
```

### print all submissions and comments by user "masta"

``` scala
scroll(user("masta")) {
	case l:Link => println("link: %s".format(l))
	case c:Comment => println("comment: " + c)
}
```

### concurrently read 2 feeds ###

``` scala
links(frontpage_top) {
	case t => println("top: " + t)
}
links(frontpage_new) {
	case t => println("new: " + t)
}
```

Thanks to Akka and Spray all web service calls happens in background threads, and are non-blocking. This allows us to run concurrent behaviors. This example crawls links from two different feeds. Each link is printed to stdout, which will result in interleaved output. An improvement might be to write the output to files.

### download images from r/pics

``` scala
import sys.process._
import akka.actor._
val execute = system.actorOf(Props(new Actor {
	def receive = {
		case cmd:String => cmd.run
	}
}))
links(subreddit("pics")) {
	case link => execute ! "wget -o /tmp/wget.log -P /tmp %s".format(link)
}
```

An actor is created to handle spawning the `wget` process. Crawling Reddit and downloading images happen concurrently!

### crawl link comments

``` scala
comments("16716l") {
	case (ancestors, c) => println("-" * ancestors.size + c.body)
}
```

`ancestors` contains this comment's parent comment, listing back to the root comment. Here I am using its `size` to format the output.

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

## TODO

* authenticated write requests (voting, link submission, commenting, etc.)
* traversal into `more` comments

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