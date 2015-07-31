package com.PredictionAlgorithm.DataDefinitions.TFL

import java.io._
import java.net.{URL, HttpURLConnection}

import akka.actor.{Props, Actor}
import com.PredictionAlgorithm.ControlInterface.StreamProcessingControlInterface._
import com.PredictionAlgorithm.DataDefinitions.LoadResource
import com.PredictionAlgorithm.DataDefinitions.TFL.LoadRouteDefinitions._
import com.PredictionAlgorithm.Database.{STOP_DEFINITIONS_COLLECTION, ROUTE_DEFINITIONS_COLLECTION, STOP_DEFINITION_DOCUMENT, ROUTE_DEFINITION_DOCUMENT}
import com.PredictionAlgorithm.Database.TFL.{TFLGetStopDefinitionDocument, TFLGetRouteDefinitionDocument, TFLInsertStopDefinition, TFLInsertUpdateRouteDefinitionDocument}

import scala.io.Source


object LoadStopDefinitions extends LoadResource {

  var percentageComplete = 0

  private val collection = STOP_DEFINITIONS_COLLECTION


  // Maps StopCode -> (StopPointName;StopPointType;Towards;Bearing;StopPointIndicator;StopPointState;Latitude;Longitude)
  private var stopDefinitionMap: Map[String, StopDefinitionFields] = Map()

  def getstopDefinitionMap: Map[String, StopDefinitionFields]  = {
    if (stopDefinitionMap.isEmpty) {
      retrieveFromDB
      stopDefinitionMap
    } else stopDefinitionMap
  }


  private def retrieveFromDB: Map[String, StopDefinitionFields] = {
    var tempMap: Map[String, StopDefinitionFields] = Map()


    val cursor = TFLGetStopDefinitionDocument.fetchAll()
    for (doc <- cursor) {
      val stopCode = doc.get(collection.STOP_CODE).asInstanceOf[String]
      val stopName = doc.get(collection.STOP_NAME).asInstanceOf[String]
      val stopType = doc.get(collection.STOP_TYPE).asInstanceOf[String]
      val towards = doc.get(collection.TOWARDS).asInstanceOf[String]
      val bearing = doc.get(collection.BEARING).asInstanceOf[Int]
      val indicator = doc.get(collection.INDICATOR).asInstanceOf[String]
      val state = doc.get(collection.STATE).asInstanceOf[Int]
      val lat = doc.get(collection.LAT).asInstanceOf[Double]
      val lng = doc.get(collection.LNG).asInstanceOf[Double]

      tempMap += (stopCode -> new StopDefinitionFields(stopName, stopType, towards, bearing, indicator, state, lat, lng))
    }
    tempMap
  }

  def updateFromWeb: Unit = {
    val streamActor = actorSystem.actorOf(Props[UpdateStopDefinitionsFromWeb], name = "UpdateStopeDefinitionsFromWeb")
    streamActor ! "start"
  }

  class UpdateStopDefinitionsFromWeb extends Actor {

    override def receive: Receive = {
      case "start" => updateFromWeb
    }

  def updateFromWeb: Unit = {

    lazy val stopList: Set[String] = TFLGetStopDefinitionDocument.getDistinctStopCodes()
    val totalNumberOfStops = stopList.size
    println("Number of stops: " + stopList.size)
    var numberLinesProcessed = 0
    var tempMap: Map[String, StopDefinitionFields] = Map()
    def tflURL(stopCode: String): String = "http://countdown.api.tfl.gov.uk/interfaces/ura/instant_V1?StopCode1=" + stopCode + "&ReturnList=StopPointName,StopPointType,Towards,Bearing,StopPointIndicator,StopPointState,Latitude,Longitude"

    println("Loading Stop Definitions From Web...")

    stopList.foreach { x =>
      val s = Source.fromURL(tflURL(x))
      s.getLines.drop(1).foreach(line => {
        val split = splitLine(line)
        tempMap += (x -> new StopDefinitionFields(split(0), split(1), split(2), split(3).toInt, split(4), split(5).toInt, split(6).toDouble, split(7).toDouble))
        numberLinesProcessed += 1
        percentageComplete = ((numberLinesProcessed.toDouble / totalNumberOfStops.toDouble) * 100).toInt
      }
      )
    }
    percentageComplete = 100
    println("Stop definitons from web loaded")
    stopDefinitionMap = tempMap
    persistToDB
  }

  private def splitLine(line: String) = line
    .substring(1, line.length - 1) // remove leading and trailing square brackets,
    .split(",(?=([^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)") // split at commas ignoring if in quotes (regEx taken from StackOverflow question: http://stackoverflow.com/questions/13335651/scala-split-string-by-commnas-ignoring-commas-between-quotes)
    .map(x => x.replaceAll("\"", "")) //take out double quotations
    .map(x => x.replaceAll(";", ",")) //take out semicolons (causes errors on read of file)
    .tail // discards the first element (always '1')


  private def persistToDB: Unit = {

    stopDefinitionMap.foreach {
      case ((stop_code), sdf: StopDefinitionFields) => {
        val newDoc = new STOP_DEFINITION_DOCUMENT(stop_code, sdf.stopPointName, sdf.stopPointType, sdf.towards, sdf.bearing, sdf.stopPointIndicator, sdf.stopPointState, sdf.latitude, sdf.longitude)
        TFLInsertStopDefinition.insertDocument(newDoc)
      }
    }
    println("Stop Definitons loaded from web and persisted to DB")

  }

}
}

