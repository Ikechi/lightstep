package node

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.event.Logging
import akka.http.scaladsl.model._, headers.HttpEncodings
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.event.Logging
import akka.util.Timeout
import akka.pattern.ask
import akka.http.scaladsl.server._
import akka.http.scaladsl.coding._
import akka.http.scaladsl.common.EntityStreamingSupport
import scala.io.StdIn
import scala.concurrent.Future
import scala.concurrent.duration._
import entities.Payload._

object Frontend extends App {
    println("Ready?")

    implicit val system = ActorSystem("lightstep")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    implicit val timeout : Timeout = 60.minutes
    implicit val jsonStreamingSupport = EntityStreamingSupport.json()

    val backend = system.actorOf(Backend.props, "Backend_Service")
    val log =  Logging(system.eventStream, "LightStep")

    val route = 
        path("analytics") {
            post {
                entity(as[Log]) { logging =>
                    log.info(s"FE got ${logging.path}")
                    onSuccess((backend ? logging.path).mapTo[ResultMetadata]){
                        case result: ResultMetadata => {
                            complete (OK, result)
                        }
                    }
                }
            }
        }
    
    val (host, port) = ("localhost", 9000)
    val bindingFuture: Future[ServerBinding] =
      Http().bindAndHandle(route, host, port)

    bindingFuture.map { serverBinding =>
        log.info(s"RestApi bound to ${serverBinding.localAddress} ")
    }.onFailure { 
        case ex: Exception =>
        log.error(ex, "Failed to bind to {}:{}!", host, port)
        system.terminate()
    }

    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ ⇒ system.terminate()) // and shutdown when done

}

//Frontend.startServer("localhost", 8080)