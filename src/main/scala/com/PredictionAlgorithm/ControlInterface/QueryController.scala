package com.PredictionAlgorithm.ControlInterface

import com.PredictionAlgorithm.Prediction.{PredictionInterface, KNNPrediction}

/**
 * Created by chrischivers on 18/07/15.
 */
class QueryController {

  //TODO get this by dependency injection
  val predictionAlgorithm:PredictionInterface = KNNPrediction

  def makePrediction(route_ID: String, direction_ID: Int, from_Point_ID: String, to_Point_ID: String, day_Of_Week: String, timeOffset: Int):String = {
    val option:Option[Int] = predictionAlgorithm.makePrediction(route_ID,direction_ID,from_Point_ID,to_Point_ID,day_Of_Week,timeOffset)
    option.getOrElse("No predction available for these parameters").toString
  }

}