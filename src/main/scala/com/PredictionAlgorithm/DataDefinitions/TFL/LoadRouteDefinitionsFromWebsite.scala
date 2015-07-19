package com.PredictionAlgorithm.DataDefinitions.TFL

import java.io.{PrintWriter, File}

import com.PredictionAlgorithm.DataDefinitions.LoadRouteDefinitionsInterface
import com.PredictionAlgorithm.DataDefinitions.TFL.LoadRouteDefinitionsFromFile._

import scala.collection.immutable.ListMap
import scala.io.Source

/**
 * Created by chrischivers on 17/07/15.
 */
object LoadRouteDefinitionsFromWebsite extends LoadRouteDefinitionsInterface{

  StopToPointSequenceMap = Map() //Empty map
  PointToStopSequenceMap = Map()


    // RouteName, WebCode
    private var routeSet: Set[(String, String)] = Set()
    private val routeListFile = new File(DEFAULT_RESOURCES_LOCATION + DEFAULT_ROUTE_LIST_FILE_NAME)

    val s = Source.fromFile(routeListFile)
    s.getLines.drop(1).foreach((line) => {
      //drop first row and iterate through others
      try {
        val splitLine = line.split(",")
        val route_ID = splitLine(0)
        val route_Web_ID = splitLine(1)
        for (direction <- 1 to 2) {
          getStopList(route_Web_ID, direction).foreach {
            case (stopCode, pointSeq, first_last) => {
              StopToPointSequenceMap += ((route_ID, direction, stopCode) ->(pointSeq, first_last))
              PointToStopSequenceMap += ((route_ID, direction, pointSeq) ->(stopCode, first_last))
            }
          }
        }
      }
      catch {
        case e: ArrayIndexOutOfBoundsException => throw new Exception("Error reading route list file. Error on line: " + line)
      }
    })

    persist

    println("Definitions from web loaded")



    private def getStopList(webRouteID: String, direction: Int): List[(String, Int, Option[String])] = {

      var stopCodeSequenceList: List[(String, Int, Option[String])] = List()

      var tflURL: String = if (direction == 1) {
        "http://m.countdown.tfl.gov.uk/showJourneyPattern/" + webRouteID + "/Outbound"
      } else if (direction == 2) {
        "http://m.countdown.tfl.gov.uk/showJourneyPattern/" + webRouteID + "/Back"
      } else {
        throw new IllegalStateException("Invalid direction ID")
      }

      val s = Source.fromURL(tflURL)
      var pointSequence = 1
      s.getLines.foreach((line) => {
        if (line.contains("<dd><a href=")) {
          val startChar: Int = line.indexOf("searchTerm=") + 11
          val endChar: Int = line.indexOf("+")
          val stopCode = line.substring(startChar, endChar)
          val first_last: Option[String] = {
            if (pointSequence == 1) Some("FIRST") else None
          }

          stopCodeSequenceList = stopCodeSequenceList :+ ((stopCode, pointSequence, first_last))
          pointSequence += 1
        }
      })

      // Set LAST on last option
      val lastone: List[(String, Int, Option[String])] = stopCodeSequenceList.takeRight(1).map { case (x, y, z) => (x, y, Some("LAST")) }
      stopCodeSequenceList = stopCodeSequenceList.dropRight(1) ::: lastone
      stopCodeSequenceList

    }

    private def persist: Unit = {

      val LINE_SEPARATOR = "\r\n";

      val pw = new PrintWriter(new File(DEFAULT_RESOURCES_LOCATION + DEFAULT_ROUTE_DEFINITIONS_FILE_NAME))
      pw.write("RouteName;Direction;TFLSequence;BusStopCode;FirstLast" + LINE_SEPARATOR) //Headers

      StopToPointSequenceMap.foreach{
        case ((route_ID, direction, stop_code),(pointSequence, first_last)) => {
          pw.write(route_ID + ";" + direction + ";" + pointSequence + ";" + stop_code + ";" + first_last.getOrElse("") + LINE_SEPARATOR)
        }
      }
      pw.close
      println("web definitions persisted to file")
    }

}
