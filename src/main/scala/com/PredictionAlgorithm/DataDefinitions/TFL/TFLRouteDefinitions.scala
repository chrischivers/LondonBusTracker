package com.PredictionAlgorithm.DataDefinitions.TFL

import java.io._
import java.lang.ArrayIndexOutOfBoundsException

import scala.io.Source
import scala.util.Try

object TFLRouteDefinitions {

  // Map format = Route_ID, Direction_ID, BusStopCode, First_Last -> pointsSequence
  private var TFLsequenceMap: Map[(String, Int, String), (Int, Option[String])] = Map()
  private var definitionsLoaded: Boolean = false

  //TODO get these through dependency injection
  val DEFAULT_RESOURCES_LOCATION = "src/main/resources/"
  val DEFAULT_ROUTE_DEFINITIONS_FILE_NAME = "routesequence.csv"
  val DEFAULT_ROUTE_LIST_FILE_NAME = "routeList.csv"

  def getTFLSequenceMap = {
    if (!definitionsLoaded) {
      //TODO find better way of choosing which
      // loadDefinitionsFromFile()
      loadDefinitionsFromWebsite(true)
    }
    TFLsequenceMap
  }


  private def loadDefinitionsFromFile(): Unit = {

    val routeDefFile = new File(DEFAULT_RESOURCES_LOCATION + DEFAULT_ROUTE_DEFINITIONS_FILE_NAME)
    TFLsequenceMap = Map() //Empty maps

    val s = Source.fromFile(routeDefFile)
    s.getLines.drop(1).foreach((line) => {
      //drop first row and iterate through others
      try {
        val splitLine = line.split(";")
        val route_ID = splitLine(0)
        val direction_ID = splitLine(1).toInt
        val pointsSequence = splitLine(2).toInt
        val busStopID = splitLine(3)
        val first_last: Option[String] = {
          if (splitLine.length == 5) Option(splitLine(4))
          else None
        }
        TFLsequenceMap += ((route_ID, direction_ID, busStopID) ->(pointsSequence, first_last))
      }
      catch {
        case e: Exception => throw new Exception("Error reading route definition file. Error on line: " + line)
      }
    })
    definitionsLoaded = true
  }

  private def loadDefinitionsFromWebsite(persistToFile: Boolean): Unit = {
    // RouteName, WebCode
    var routeSet: Set[(String, String)] = Set()
    val routeListFile = new File(DEFAULT_RESOURCES_LOCATION + DEFAULT_ROUTE_LIST_FILE_NAME)

    val s = Source.fromFile(routeListFile)
    s.getLines.drop(1).foreach((line) => {
      //drop first row and iterate through others
      try {
        val splitLine = line.split(",")
        val route_ID = splitLine(0)
        val route_Web_ID = splitLine(1)
        for (direction <- 1 to 2) {
          getStopList(route_ID, direction).foreach {
            case (stopCode, pointSeq, first_last) => TFLsequenceMap += ((route_ID, direction, stopCode) ->(pointSeq, first_last))
          }
        }
      }
      catch {
        case e: ArrayIndexOutOfBoundsException => throw new Exception("Error reading route list file. Error on line: " + line)
      }
    })

    if (persistToFile) {persist}

    definitionsLoaded = true
    println("Web definitions loaded")



    def getStopList(routeID: String, direction: Int): List[(String, Int, Option[String])] = {

      var stopCodeSequenceList: List[(String, Int, Option[String])] = List()

      var tflURL: String = if (direction == 1) {
        "http://m.countdown.tfl.gov.uk/showJourneyPattern/" + routeID + "/Outbound"
      } else if (direction == 2) {
        "http://m.countdown.tfl.gov.uk/showJourneyPattern/" + routeID + "/Back"
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

    def persist: Unit = {

      val LINE_SEPARATOR = "\r\n";

      val pw = new PrintWriter(new File(DEFAULT_RESOURCES_LOCATION + DEFAULT_ROUTE_DEFINITIONS_FILE_NAME))
      pw.write("RouteName;Direction;TFLSequence;BusStopCode;FirstLast" + LINE_SEPARATOR) //Headers

      TFLsequenceMap.foreach{
        case ((route_ID, direction, stop_code),(pointSequence, first_last)) => {
          pw.write(route_ID + ";" + direction + ";" + pointSequence + ";" + stop_code + ";" + first_last.getOrElse("") + LINE_SEPARATOR)
        }
      }
      pw.close
      println("web definitions persisted to file")
    }
  }
}