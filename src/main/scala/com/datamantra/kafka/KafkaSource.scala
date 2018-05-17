package com.datamantra.kafka

import com.datamantra.creditcard.TransactionKafka
import org.apache.spark.sql.{SparkSession, Dataset}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

/**
 * Created by kafka on 16/5/18.
 */
object KafkaSource {


  val rawTransactionStructureName = "rawTransaction"
  val rawtransactionSchema = new StructType()
      .add("cc_num", StringType,true)
      .add("first", StringType, true)
      .add("last", StringType, true)
      .add("transactionId", StringType, true)
      .add("transactionDate", StringType, true)
      .add("transactionTime", StringType, true)
      .add("unixTime", StringType, true)
      .add("category", StringType, true)
      .add("merchant", StringType, true)
      .add("amt", StringType, true)
      .add("merchlat", StringType, true)
      .add("merchlong", StringType, true)


  def readStream(startingOption: String = "startingOffsets", partitionsAndOffsets: String = "earliest")(implicit sparkSession:SparkSession) = {
    println("Reading from Kafka")

    import  sparkSession.implicits._
    sparkSession
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("subscribe", "creditTransaction")
      .option("enable.auto.commit", false) // Cannot be set to true in Spark Strucutured Streaming https://spark.apache.org/docs/latest/structured-streaming-kafka-integration.html#kafka-specific-configurations
      .option("group.id", "Structured-Streaming-Examples")
      .option("failOnDataLoss", false) // when starting a fresh kafka (default location is temporary (/tmp) and cassandra is not (var/lib)), we have saved different offsets in Cassandra than real offsets in kafka (that contains nothing)
      .option(startingOption, partitionsAndOffsets) //this only applies when a new query is started and that resuming will always pick up from where the query left off
      .load()
      .withColumn(rawTransactionStructureName, // nested structure with our json
       from_json($"value".cast(StringType), KafkaSource.rawtransactionSchema)) //From binary to JSON object
      .as[TransactionKafka]
  }
}