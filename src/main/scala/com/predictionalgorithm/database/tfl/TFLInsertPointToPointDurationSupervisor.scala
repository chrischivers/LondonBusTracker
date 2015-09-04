package com.predictionalgorithm.database.tfl

import java.io.{File, FileWriter}

import akka.actor.{ActorSystem, ActorRef, Props, Actor}
import akka.routing.{RoundRobinPool, RoundRobinRouter}
import com.mongodb.util.JSON
import com.predictionalgorithm.database._
import com.mongodb.casbah.MongoCursor
import com.mongodb.casbah.commons.{Imports, MongoDBObject}
import com.mongodb.casbah.Imports._

import scala.io.Source

object TFLInsertPointToPointDurationSupervisor extends DatabaseInsert {

  @volatile var numberDBPullTransactionsRequested: Long = 0

  override protected val collection: DatabaseCollections = POINT_TO_POINT_COLLECTION
 // override protected val dbTransactionActor: ActorRef = actorSystem.actorOf(Props[TFLInsertPointToPointDurationActor], "InsertPointToPointActor")
 //val a1 = actorSystem.actorOf(Props[TFLInsertPointToPointDurationActor], "actor1")
 // val a2 = actorSystem.actorOf(Props[TFLInsertPointToPointDurationActor], "actor2")
  val routerProps = actorSystem.actorOf(RoundRobinPool(10).props(Props[TFLInsertPointToPointDurationActor]), "InsertPointToPointRouter")
  override protected val dbTransactionActor: ActorRef = routerProps
}

class TFLInsertPointToPointDurationActor extends Actor {

  val collection = POINT_TO_POINT_COLLECTION


  val PRUNE_THRESHOLD_K_LIMIT = 10
  val PRUNE_THRESHOLD_TIME_LIMIT = 1800
  val PRUNE_THRESHOLD_RAINFALL_LIMIT = 0.5


  override def receive: Receive = {
    case doc1: POINT_TO_POINT_DOCUMENT =>  insertToDB(doc1)
    case _ => throw new IllegalStateException("TFL Insert Point Actor received unknown message")
  }


  private def insertToDB(doc: POINT_TO_POINT_DOCUMENT) = {

    val newObj = MongoDBObject(
      collection.ROUTE_ID -> doc.route_ID,
      collection.DIRECTION_ID -> doc.direction_ID,
      collection.FROM_POINT_ID -> doc.from_Point_ID,
      collection.TO_POINT_ID -> doc.to_Point_ID,
      collection.DAY -> doc.day_Of_Week)

    val objectID = pruneExistingCollectionBeforeInsert(newObj, doc.timeOffsetSeconds, doc.rainfall)
    val pushUpdate = $push(collection.DURATION_LIST -> MongoDBObject(collection.DURATION -> doc.durationSeconds, collection.TIME_OFFSET -> doc.timeOffsetSeconds, collection.RAINFALL -> doc.rainfall, collection.TIME_STAMP -> System.currentTimeMillis()))

        // If there is already an object ID (doesn't already exist)
    if (objectID.isEmpty) {
      TFLInsertPointToPointDurationSupervisor.dBCollection.update(newObj, pushUpdate, upsert = true)
      TFLInsertPointToPointDurationSupervisor.numberDBTransactionsExecuted += 1
    } else {
      val newObjWithID = MongoDBObject(
        "_id" -> objectID.get
      )

      TFLInsertPointToPointDurationSupervisor.dBCollection.update(newObjWithID, pushUpdate, upsert = true)
      TFLInsertPointToPointDurationSupervisor.numberDBTransactionsExecuted += 1
    }
  }


  /**
   * Prune existing records in collection before inserting a new record (ensures database does not grow infinitely)
   * @param newObj The new object to be inserted
   * @param timeOffSet The time offset of the new record to be inserted
   * @param rainfall The rainfall of the new record to be inserted
   * @return The objectID to insert
   */
  def pruneExistingCollectionBeforeInsert(newObj: MongoDBObject, timeOffSet: Int, rainfall: Double): Option[String] = {
    val cursor: MongoCursor = TFLGetPointToPointDocument.executeQuery(newObj)
    if (cursor.size > 0) {
      //If no entry in DB with route, direction, fromPoint and toPoint... do nothing
      assert(cursor.length == 1)
      val record = cursor.next().asInstanceOf[Imports.BasicDBObject]

      val objectID = record.getString("_id")

      val newObjWithID = MongoDBObject(
        "_id" -> objectID
      )

      val durListVector = getDurListVectorFromCursor(record)

      // This filters those within the PRUNE THRESHOLD LIMIT followed by those within the rainfall threshold
      val prunedVector = durListVector.filter(x =>
        math.abs(x._2 - timeOffSet) <= PRUNE_THRESHOLD_TIME_LIMIT &&
          math.abs(x._4 - rainfall) <= PRUNE_THRESHOLD_RAINFALL_LIMIT)

      if (prunedVector.size > PRUNE_THRESHOLD_K_LIMIT) {
        val entryToDelete = prunedVector.minBy(_._3) //Gets the oldest record in the vector
        val updatePull = $pull(collection.DURATION_LIST -> MongoDBObject(collection.DURATION -> entryToDelete._1, collection.TIME_OFFSET -> entryToDelete._2, collection.TIME_STAMP -> entryToDelete._3, collection.RAINFALL -> entryToDelete._4))

          TFLInsertPointToPointDurationSupervisor.dBCollection.update(newObjWithID, updatePull)
        TFLInsertPointToPointDurationSupervisor.numberDBPullTransactionsRequested += 1

      }
      Some(objectID)
    } else None
  }


  /*
   *
   * @param dbObject The database document
   * @return A vector of Duration, Time Offset, Time Stamp and Time Offset Difference
   */
  def getDurListVectorFromCursor(dbObject: Imports.MongoDBObject): Vector[(Int, Int, Long, Double)] = {
    dbObject.get(collection.DURATION_LIST).get.asInstanceOf[Imports.BasicDBList].map(y => {
      (y.asInstanceOf[Imports.BasicDBObject].getInt(collection.DURATION),
        y.asInstanceOf[Imports.BasicDBObject].getInt(collection.TIME_OFFSET),
        y.asInstanceOf[Imports.BasicDBObject].getLong(collection.TIME_STAMP),
        y.asInstanceOf[Imports.BasicDBObject].getDouble(collection.RAINFALL))
    })
      .toVector
  }


}

