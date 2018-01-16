package entities

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import akka.actor.ActorRef
//import scala

object Payload extends SprayJsonSupport with DefaultJsonProtocol {
    case class Payload (
        service: String,
        level: String,
        timestamp: String,
        operation: String,
        message: String,
        transaction_id: String
    ){
        def message_hook: String = message.split(' ')(0)
    }

    case class Log(path: String)

    trait AnalyticsResult
    case class HighestErrorCount(operation: String, errorCount: Long) extends AnalyticsResult
    case class LongestTransaction(transaction_id: String, timeElapsed: String) extends AnalyticsResult
    case class Result(
        highestErrorCount: HighestErrorCount,
        longestTransaction: LongestTransaction
    )

    case class PayloadRef(payload: Payload, ref: ActorRef)
    case class ResultRef(result: Result, ref: ActorRef)

    case class Metadata(bytesRead: Long)
    case class ResultMetadata(result: Result, metadata: Metadata)

    implicit val PayloadFormat = jsonFormat6(Payload.apply)
    implicit val LogFormat = jsonFormat1(Log.apply)
    implicit val HighestErrorCountFormat = jsonFormat2(HighestErrorCount.apply)
    implicit val LongestTransactionFormat = jsonFormat2(LongestTransaction.apply)
    implicit val MetadataFormat = jsonFormat1(Metadata.apply)
    implicit val ResultFormat = jsonFormat2(Result.apply)
    implicit val ResultMetadataFormat = jsonFormat2(ResultMetadata.apply)
}