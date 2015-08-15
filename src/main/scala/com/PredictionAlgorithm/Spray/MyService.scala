/*package com.PredictionAlgorithm.Spray


import akka.actor.{ActorSystem, ActorLogging, Props, Actor}
import akka.io.Tcp
import com.PredictionAlgorithm.Commons.Commons
import com.PredictionAlgorithm.ControlInterface.{LiveStreamControlInterface, QueryController}
import com.PredictionAlgorithm.DataDefinitions.TFL.TFLDefinitions
import com.PredictionAlgorithm.Streaming.{PackagedStreamObject, LiveStreamResult, LiveStreamingCoordinator}
import spray.http.CacheDirectives.`no-cache`
import spray.http.HttpHeaders.`Cache-Control`
import spray.routing._
import spray.http._
import MediaTypes._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._
import spray.http.HttpHeaders.{`Content-Type`, Connection, `Cache-Control`}

import scala.concurrent.duration._


// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class MyServiceActor extends Actor with MyService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = {
    //runRoute(myRoute)
    runRoute(thisRoute)
  }


}


// this trait defines our service behavior independently from the service actor
trait MyService extends HttpService {

  implicit def executionContext = actorRefFactory.dispatcher

  val streamFields = Array("reg","nextArr","movementData","routeID", "directionID", "towards","nextStopID","nextStopName")
  val `text/event-stream` = MediaType.custom("text/event-stream")
  MediaTypes.register(`text/event-stream`)



  class sendStream(routeIDs:Array[String]) {

    case class Ok()

    // This streaming method has been adapted from a demo at https://github.com/chesterxgchen/sse-demo
    def sendSSE(ctx: RequestContext): Unit = {

      val streamImpl: FIFOStreamImplementation = new FIFOStreamImplementation(routeIDs)
      LiveStreamingCoordinator.registerNewStream(streamImpl)
      val stream: Iterator[PackagedStreamObject] = streamImpl.toStream.iterator

      actorRefFactory.actorOf {
        Props {
          new Actor {
            // we use the successful sending of a chunk as trigger for scheduling the next chunk
            val responseStart = HttpResponse(entity = HttpEntity(`text/event-stream`, "data: start\n\n"))
            ctx.responder ! ChunkedResponseStart(responseStart).withAck(Ok)

            def receive = {
              case Ok =>
                // in(Duration(500, MILLISECONDS)) {
                val next = stream.next()
                val nextList = Map(
                  streamFields(0) -> next.reg,
                  streamFields(1) -> next.nextArrivalTime,
                  streamFields(2) -> compact(render(next.markerMovementData.map({case(lat,lng,rot,propDist,labx,laby) => lat + "," + lng + "," + rot + "," + propDist + "," + labx + "," + laby }).toList)),
                  streamFields(3) -> next.route_ID,
                  streamFields(4) -> next.direction_ID.toString,
                  streamFields(5) -> next.towards,
                  streamFields(6) -> next.nextStopID,
                  streamFields(7) -> next.nextStopName)
                val json = compact(render(nextList))

                val nextChunk = MessageChunk("data: " + json + "\n\n")
                ctx.responder ! nextChunk.withAck(Ok)

              case ev: Tcp.ConnectionClosed => LiveStreamingCoordinator.deregisterStream(streamImpl)
            }
          }
        }
      }
    }


    def in[U](duration: FiniteDuration)(body: => U): Unit =
      ActorSystem().scheduler.scheduleOnce(duration)(body)
  }

  def respondAsEventStream =
    respondWithHeader(`Cache-Control`(`no-cache`)) &
      respondWithHeader(`Connection`("Keep-Alive")) &
      respondWithMediaType(`text/event-stream`)



}
*/
