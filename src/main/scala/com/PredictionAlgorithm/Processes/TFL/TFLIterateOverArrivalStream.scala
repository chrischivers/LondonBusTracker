package com.PredictionAlgorithm.Processes.TFL


import java.net.UnknownHostException

import akka.actor.{Props, Actor}
import com.PredictionAlgorithm.ControlInterface.DataReadProcessStoreControlInterface._
import com.PredictionAlgorithm.DataSource.TFL.{TFLSourceLineFormatter, TFLDataSource, TFLSourceLine}
import com.PredictionAlgorithm.DataSource._
import com.PredictionAlgorithm.Database.POINT_TO_POINT_COLLECTION
import com.PredictionAlgorithm.Processes.{IterateOverArrivalStreamInterface}

import scala.util.{Failure, Success, Try}


class TFLIterateOverArrivalStream extends IterateOverArrivalStreamInterface {

  val iteratingActor = actorSystem.actorOf(Props[IteratingActor], name = "IteratorStream")

  override def start = {
      iteratingActor ! "start"
      iteratingActor ! "next"
  }

  override def stop = {
    iteratingActor ! "stop"
  }
}


object TFLIterateOverArrivalStream {
  @volatile var numberProcessed:Long = 0

}

//TODO consider abstracting this to an interface
class IteratingActor extends Actor {
  var it = getSourceIterator

  // Iterating pattern for this actor based on code snippet posted on StackOverflow
  //http://stackoverflow.com/questions/5626285/pattern-for-interruptible-loops-using-actors


  override def receive: Receive = inactive // Start out as inactive

  def inactive: Receive = { // This is the behavior when inactive
    case "start" =>
      context.become(active)
  }

  def active: Receive = { // This is the behavior when it's active
    case "stop" =>
      context.become(inactive)
    case "next" =>
      try {
        val line = TFLSourceLineFormatter(it.next())
        TFLProcessSourceLines(line)
        TFLIterateOverArrivalStream.numberProcessed += 1
        self ! "next"
      } catch{
        case iae: IllegalArgumentException=> {
          println("Error reading source.")
          context.become(inactive)
          self ! "start"
        }
        case e: Exception => println("Error reading line: " + e)

      }

  }

  def getSourceIterator =
    Try(new SourceIterator(new HttpDataStream(TFLDataSource))) match {
      case Success(src) => src.iterator
      case Failure(fail) => throw new IllegalStateException("Cannot get Source Iterator")
    }

}