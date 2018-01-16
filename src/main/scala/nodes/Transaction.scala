package nodes

import akka.actor.{ActorRef, Actor, Props, ActorLogging}
import akka.pattern.ask
import akka.util.Timeout
import entities.Payload._
import services._
import scala.concurrent.duration._
import scala.concurrent.Future

object Transaction {
    def props(id: String) = Props(classOf[Transaction], id)
}

class Transaction(id: String) extends Actor with ActorLogging {
    var count: Long = 0
    var errorAnalytics: ActorRef = _
    var timeAnalytics: ActorRef = _

    override def preStart = {
        log.info(s"${self.path.name} started")
        errorAnalytics = context.actorOf(ErrorAnalytics.props, s"${self.path.name}_ErrorAnalytics")
        timeAnalytics = context.actorOf(TimeAnalytics.props(id), s"${self.path.name}_TimeAnalytics")
    }

    def receive = {
        case p: PayloadRef => {
            val (payload, response) = (p.payload, p.ref)
            count = count + 1
            val ref: ActorRef = sender()
            implicit val timeout: Timeout = 60.minutes
            implicit val ec = context.dispatcher
            val future_errorAnalytics = (errorAnalytics ? payload).mapTo[HighestErrorCount]
            val future_timeAnalytics = (timeAnalytics ? payload).mapTo[LongestTransaction]
            val composed = future_errorAnalytics.zipWith(future_timeAnalytics)(composeResult)
            composed.foreach { result =>
                ref ! ResultRef(result, response)
            }
        }

        case msg @ _ => {
            sender() ! Result(HighestErrorCount("incoming", 100), LongestTransaction("incoming", "12 sec"))
        }
    }

    def composeResult(err: HighestErrorCount, timing: LongestTransaction): Result = {
        Result(err, timing)
    }

    override def postStop = {
        log.info(s"${self.path.name} shutting down")
    }
}