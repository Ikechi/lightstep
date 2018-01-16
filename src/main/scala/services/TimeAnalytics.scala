package services

import akka.actor.{Actor, Props, ActorLogging, ActorRef}
import entities.Payload._
import java.time.LocalDateTime
import java.time.Duration
import scala.collection.immutable.ListMap

object TimeAnalytics {
    def props(id: String): Props = Props(classOf[TimeAnalytics], id)
}

class TimeAnalytics(id: String) extends Actor with ActorLogging {
    var start: String = "null"
    var end: String = "null"

    override def preStart = {
        log.info(s"${self.path.name} Service Started")
    }

    def receive = {
        case payload: Payload => {
            val ref: ActorRef = sender()
            val hook = payload.message_hook
            hook match {
                case "START" => {
                    if(start == "null")
                        start = payload.timestamp
                }

                case "END" => {
                    end = payload.timestamp
                }

                case _ => 
            }
            
            ref ! LongestTransaction(id, getTimeDiff((start, end)).toString)
        }
    }

    def getTimeDiff(timing: (String, String)): Duration = {
        timing match {
            case (_, "null") => {
                Duration.between(LocalDateTime.now, LocalDateTime.now)
            }

            case ("null", _) => {
                Duration.between(LocalDateTime.now, LocalDateTime.now)
            }

            case _ => {
                val start = LocalDateTime.parse(timing._1.replace(' ', 'T'));
                val end = LocalDateTime.parse(timing._2.replace(' ', 'T'));
                Duration.between(start, end)
            }
        }
    }


    override def postStop = {
        log.info(s"${self.path.name} Service Shutting down")
    }
}