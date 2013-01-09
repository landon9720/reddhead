package kuhn

import akka.actor.{Props, ActorSystem}
import spray.can.client.HttpClient
import spray.client.HttpConduit
import akka.contrib.throttle.TimerBasedThrottler
import spray.client.HttpConduit._
import akka.contrib.throttle.Throttler.SetTarget
import akka.contrib.throttle.Throttler.Rate
import scala.Some
import spray.io.IOExtension
import concurrent.duration._

object system {

	implicit val system = ActorSystem()

	val ioBridge = IOExtension(system).ioBridge()

	val httpClient = system.actorOf(Props(new HttpClient(ioBridge)))

	val conduit = system.actorOf(Props(new HttpConduit(httpClient, "www.reddit.com", 80)))

	val throttler = system.actorOf(Props(new TimerBasedThrottler(Rate(1, 3 seconds))))
	throttler ! SetTarget(Some(conduit))

	val pipeline = sendReceive(throttler)

}
