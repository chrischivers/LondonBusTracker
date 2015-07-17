package com.PredictionAlgorithm.Database


/**
 * Created by chrischivers on 20/06/15.
 */
sealed trait DatabaseCollections {
  val name: String
  val fieldsVector: Vector[String]
  val indexKeyList: List[(String,Int)]
}

final case object POINT_TO_POINT_COLLECTION extends DatabaseCollections {


  val ROUTE_ID = "ROUTE_ID"
  val DIRECTION_ID = "DIRECTION_ID"
  val FROM_POINT_ID = "FROM_POINT_ID"
  val TO_POINT_ID = "TO_POINT_ID"
  val DAY_TYPE = "DAY_TYPE"
  val DURATION_LIST = "DURATION_LIST"
  val OBSERVED_TIME = "OBSERVED_TIME"
  val DURATION = "DURATION"
  val LAST_UPDATED = "LAST_UPDATED"

  override val name: String = "PointToPoint"
  override val fieldsVector = Vector(ROUTE_ID, DIRECTION_ID, FROM_POINT_ID, TO_POINT_ID, DAY_TYPE, OBSERVED_TIME, DURATION, LAST_UPDATED)
  override val indexKeyList = List((ROUTE_ID, 1),(DIRECTION_ID, 1),(FROM_POINT_ID, 1),(TO_POINT_ID, 1),(DAY_TYPE, 1))

}
