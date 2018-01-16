package services

import nodes._
import akka.actor.{Actor, Props, ActorRef, ActorLogging, Terminated}
import akka.Done
import entities.Payload._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scala.collection.mutable.ListMap
import java.time.LocalDateTime
import java.time.Duration

object LoggingAnalytics {
    def props: Props = Props[LoggingAnalytics]
}

class LoggingAnalytics extends Actor with ActorLogging {
    var count: Int = 0
    var errorResult: Map[String, Long] = Map.empty[String, Long]
    var longestTransaction: Tuple2[String, Duration] = ("null", Duration.between(LocalDateTime.now, LocalDateTime.now))

    override def preStart = {
        log.info(s"${self.path.name} Service Started")
    }

    def receive = {
        case payload: Payload => {
            count = count + 1
            val ref: ActorRef = sender()
            implicit val timeout: Timeout = 60.minutes
            implicit val ec = context.dispatcher
            val id = payload.transaction_id
            val child_ref_option = context.child(id)
            val child_ref = child_ref_option match {
                case Some(ref) => ref
                case None => {
                    val ref = context.actorOf(Transaction.props(id), id)
                    context.watch(ref)
                    ref
                }
            }
            val future_result = child_ref ? PayloadRef(payload, ref)
            val result_ref = Await.result(future_result, timeout.duration).asInstanceOf[ResultRef]
            
            val operation = result_ref.result.highestErrorCount.operation
            val errCount = result_ref.result.highestErrorCount.errorCount
            val currvalue: Long = errorResult.getOrElse(operation, 0)
            errorResult = errorResult + (operation -> (currvalue + errCount))
            var temp = (errorResult.head._1, errorResult.head._2)
            for((k, v) <- errorResult){
                if(v > temp._2) temp = (k,v)
            }
            val currTiming = Duration.parse(result_ref.result.longestTransaction.timeElapsed.replace(' ', 'T'))
            if(currTiming.compareTo(longestTransaction._2) > 0)
                longestTransaction = (result_ref.result.longestTransaction.transaction_id, currTiming)
            val err_final = HighestErrorCount(temp._1, temp._2)
            val timing = LongestTransaction(longestTransaction._1, longestTransaction._2.toNanos.toString + " nsec")

            result_ref.ref ! Result(err_final, timing)
        }

        case msg @ _ => {
            sender() ! Result(HighestErrorCount("incoming", 100), LongestTransaction("incoming", "12 sec"))
        }

    }

    override def postStop = {
        log.info(s"${self.path.name} Service Shutting down")
    }
}