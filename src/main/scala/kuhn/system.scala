package kuhn

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.{ActorSystem, Props, actorRef2Scala}
import akka.contrib.throttle.Throttler.{Rate, SetTarget}
import akka.contrib.throttle.TimerBasedThrottler
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http
import spray.client.pipelining.{WithTransformerConcatenation, addHeader, sendReceive}
import spray.http.{HttpRequest, HttpResponse}

object system {

	implicit val timeout = Timeout(7 days)
	implicit val system = ActorSystem("ClusterSystem")
	import system.dispatcher
	
	private val throttler = system.actorOf(Props(classOf[TimerBasedThrottler], (Rate(1, 3 seconds))))
	throttler ! SetTarget(Some(IO(Http)))
	
	val pipeline : HttpRequest => Future[HttpResponse] = (
		addHeader("User-Agent", "landon9720_reddhead version 1.1")
		~> addHeader("Host", "www.reddit.com")
		~> sendReceive(throttler)
	)
	
}
