package com.scinia

import com.scinia.LoaderId.LoaderId
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.data.validation.ValidationError

import com.scinia.Tables._
// import com.scinia.CommonReads._
import com.typesafe.scalalogging.LazyLogging

/**
 * Ids for types of data sources we support or want to support
 */
object LoaderId extends Enumeration {
  type LoaderId = Value
  val SMS      = Value(1)
  val GVOICE   = Value(2)
  val LATITUDE = Value(3)
  val HANGOUTS = Value(4)
  val TWEETS   = Value(5)
  val FACEBOOK = Value(6)
  val RDIO     = Value(7)
  val SKYPE    = Value(8)
  val GCHAT    = Value(9)
}

/**
 * A completely generic model of an instant message
 * Applicable to any IM client.
 */
case class ChatRecord(
  date: String, // 2014-08-22 15:55:39 (or any ISO8601 timestring)
  from: String, // Whatever the primary identifier string is, e.g. phone # or name
  to:   String, // ditto
  text: String,
  source: LoaderId // every chat has to come from somewhere
)

abstract class Loader[T] extends Log {
  def apply(path: String): Seq[T]
}

/**
 * Base class for building loaders that convert JSON
 * from various types of IM history files into Seq[ChatRecord]
 */
abstract class JsonMessageLoader[A] extends Loader[ChatRecord] {
  /**
   * @param The path to a JSON file containing IM history
   * @return a bunch of ChatRecords
   */
  def apply(path: String): Seq[ChatRecord] =
    Json.parse(
      io.Source.fromFile(path).getLines.mkString
    )
    .validate[List[A]](reader) match {
      case JsSuccess(records, _) => toChatRecords(records)
      case JsError(errors)       => { Log(errors.toString); Nil }
    }

  /**
   * A Play Scala Reads capable of parsing the JSON object located at path
   */

  val reader: Reads[List[A]]
  /**
   * A method to convert from reader's output (List[A]) to a Seq[ChatRecords]
   */
  def toChatRecords(records: List[A]): Seq[ChatRecord]
}

/*
 * For loading JSON data that is already in the chat record format
 * so no special loading logic is needed.
 */
abstract class ChatRecordLoader extends Loader[ChatRecord] {

  override def apply(path: String): Seq[ChatRecord] =
    io.Source.fromFile(path).getLines.toSeq.flatMap { line =>
      Json.parse(line)
        .validate[ChatRecord](reader) match {
          case JsSuccess(record, _) => Some(toChatRecord(record))
          case JsError(errors) =>
            logger.debug(s"could not parse $path: $errors")
            None
        }
    }

  def toChatRecord(record: ChatRecord) = record

  // an implicit reads for the type LoaderId which is the only non native field in a ChatRecord
  // http://stackoverflow.com/questions/14754092/how-to-turn-json-to-case-class-when-case-class-has-only-one-field
  // This is the most frustrating library I have ever used you have no idea how long it took me to write
  // this lasdkfjals;dkfjas;dlkfjasd;flk
  // EDIT: also this line must come before Json.reads[ChatRecord] or the JSON macro throws an NPE
  implicit val LoaderIdReader: Reads[LoaderId] = ((__).read[Int]).map { id => LoaderId(id)}
  
  val reader = Json.reads[ChatRecord]
}
