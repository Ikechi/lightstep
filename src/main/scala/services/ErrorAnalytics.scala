package services

import akka.actor.{Actor, Props, ActorLogging, ActorRef}
import entities.Payload._
import scala.collection.immutable.ListMap

object ErrorAnalytics {
    def props: Props = Props[ErrorAnalytics]
}

class ErrorAnalytics extends Actor with ActorLogging {

    var errorList:  Set[String] = Set.empty[String]
    var err = HighestErrorCount("null", 0)

    override def preStart = {
        log.info(s"${self.path.name} Service Started")
    }

    def receive = {
        case payload: Payload => {
            val ref: ActorRef = sender()

            val level = payload.level
            val operation = payload.operation
            err = HighestErrorCount(operation, 0)
            if(level == "ERROR"){
                if(errorList contains operation){
                    err = HighestErrorCount(operation, 0)
                }
                else {
                    errorList = errorList + operation
                    err = HighestErrorCount(operation, 1)
                }
            }
            //log.info(s"SET is now $errorList")
            ref ! err
        }
    }
    override def postStop = {
        log.info(s"${self.path.name} Service Shutting down")
    }
}