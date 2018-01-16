package node

import entities.Payload._
import services._
import akka.actor.{Actor, Props, ActorLogging, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import akka.{Done, NotUsed}
import akka.stream.{ ActorMaterializer, IOResult}
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.{FileIO, RunnableGraph, Source, Sink, Flow, JsonFraming}
import akka.util.ByteString
import java.nio.file.Paths
import spray.json._
import spray.json.DefaultJsonProtocol

object Backend {
    def props: Props = Props[Backend]
}

class Backend extends Actor with ActorLogging {
    var client: Int = _
    implicit val ec = context.dispatcher
    implicit val materializer = ActorMaterializer()
    override def preStart = {
        log.info("Backend Started")
        var client: Int = 0
    }

    def receive = {
        case msg: String  => {
            log.info(s"I got ${msg} from frontend")
            client = client + 1
            var count: Long = 0
            var res: Result = Result(HighestErrorCount("incoming", 0), LongestTransaction("incoming", "12 sec"))
            var err: AnalyticsResult = HighestErrorCount("null", 0)
            implicit val timeout: Timeout = 60.minutes
            val ref: ActorRef = sender()
            val loggingAnalytics = context.actorOf(LoggingAnalytics.props, s"LoggingAnalytics_${client}")
            val source: Source[ByteString, Future[IOResult]] = FileIO.fromPath(Paths.get(msg))

            val flow: Flow[ByteString, Payload, NotUsed] = 
                JsonFraming.objectScanner(Int.MaxValue)
                .map(_.decodeString("UTF8").parseJson.convertTo[Payload])

            val sink: Sink[Result, Future[Result]] = 
                Sink.last

            val analytics: Flow[Payload, Result, NotUsed] = 
                Flow[Payload]
                    .mapAsync(parallelism = 5)(p => (loggingAnalytics ? p).mapTo[Result])

            val runnableGraph: RunnableGraph[(Future[IOResult], Future[Result])] = source.via(flow).via(analytics).toMat(sink)(Keep.both)

            val data = runnableGraph.run()

            val ioresult = Await.result(data._1, timeout.duration).asInstanceOf[IOResult]

            count = ioresult.count
            log.info(s"${count} bytes read and exited with ${ioresult.status}")


            data._2.foreach { res => 
                ref ! ResultMetadata(res, Metadata(ioresult.count))
                context.stop(loggingAnalytics)
            }
        }
    }

    override def postStop = {
        log.info(s"${self.path.name} Service Shutting down")
    }
}