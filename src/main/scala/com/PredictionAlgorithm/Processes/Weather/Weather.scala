package com.PredictionAlgorithm.Processes.Weather

import java.util.Calendar

import scala.io.Source

case class rainFall (rainfall:Double, validTo:Long)

object Weather {

  val WEATHER_API_URL = "http://api.openweathermap.org/data/2.5/forecast/city?id=2643743&mode=xml&APPID=e236bab1ce50fe2b7c7fd581b2e467f1"

  def getCurrentRainFall: rainFall = {
    val s = Source.fromURL(WEATHER_API_URL)
    val line = s.getLines.next()

    def helper(startIndex:Int):rainFall = {
      println("weather fetched")


      val timeFromStartPoint = line.indexOf("time from=", startIndex)
      val timeToStartPoint = line.indexOf("to=", timeFromStartPoint) + 4
      val year = line.substring(timeToStartPoint, timeToStartPoint + 4).toInt
      val month = line.substring(timeToStartPoint + 5, timeToStartPoint + 7).toInt
      val day = line.substring(timeToStartPoint + 8, timeToStartPoint + 10).toInt
      val hour = line.substring(timeToStartPoint + 11, timeToStartPoint + 13).toInt

      val cal = Calendar.getInstance()
      cal.set(Calendar.YEAR, year)
      cal.set(Calendar.MONTH, month - 1)
      cal.set(Calendar.DAY_OF_MONTH, day)
      cal.set(Calendar.HOUR_OF_DAY, hour)
      cal.set(Calendar.MINUTE, 0)
      cal.set(Calendar.SECOND, 0)
      cal.set(Calendar.MILLISECOND, 0)

      if (cal.getTimeInMillis - System.currentTimeMillis() < 0) helper(timeToStartPoint)
      else {
        if (line.indexOf("<precipitation/>", timeToStartPoint) != -1 && line.indexOf("<precipitation/>", timeToStartPoint) < line.indexOf("<precipitation", timeToStartPoint)) new rainFall(0.0, 0)
        else {
          val lineStartPoint = line.indexOf("<precipitation", timeToStartPoint)
          val valueStartPoint = line.indexOf("value", lineStartPoint) + 7
          val valueEndPoint = line.indexOf("\"", valueStartPoint)
          val rainFallValue = line.substring(valueStartPoint, valueEndPoint).toDouble

          new rainFall(rainFallValue, cal.getTimeInMillis)
        }
      }

    }
    helper(0)
  }

}
