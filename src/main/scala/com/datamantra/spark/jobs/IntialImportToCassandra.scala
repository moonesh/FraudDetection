package com.datamantra.spark.jobs

import com.datamantra.cassandra.{CassandraConfig, CassandraDriver}
import com.datamantra.config.Config
import com.datamantra.creditcard.Schema
import com.datamantra.spark.{DataReader, SparkConfig}
import com.datamantra.utils.Utils
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{TimestampType, IntegerType}

/**
 * Created by kafka on 24/5/18.
 */
object IntialImportToCassandra extends SparkJob("Initial Import to Cassandra"){

  def main(args: Array[String]) {

    Config.parseArgs(args)

    
    import sparkSession.implicits._
    
    //This is windows shit 
    System.setProperty("hadoop.home.dir", "C:\\data\\CreditCardFraud");
 
   
   
    //Read Customer data from csv and load into Data Frame 
    val customerDF = DataReader.read(SparkConfig.customerDatasource, Schema.customerSchema)

    // Save Customer data to cassandra directly "as is"
    customerDF.write
      .format("org.apache.spark.sql.cassandra")
      .mode("append")
      .options(Map("keyspace" -> CassandraConfig.keyspace, "table" -> CassandraConfig.customer))
      .save()

    
      
      //Now add make another Data Frame with an additional columnn  i.e.  "age" -  calculated as currentdate - dob
      val customerAgeDF = customerDF.withColumn("age", (datediff(current_date(),to_date($"dob"))/365).cast(IntegerType))

      
     
      val transactionDF0 = DataReader.read(SparkConfig.transactionDatasouce, Schema.fruadCheckedTransactionSchema)
    
      transactionDF0.show();
      val transactionDF1=  transactionDF0.withColumn("trans_date", split($"trans_date", "T").getItem(0))
     
    transactionDF1.show()
    
    val transactionDF2 = transactionDF1.withColumn("trans_time", concat_ws(" ", $"trans_date", $"trans_time"))
  
     transactionDF2.show()
     
    val transactionDF=  transactionDF2.withColumn("trans_time", to_timestamp($"trans_time", "YYYY-MM-dd HH:mm:ss") cast(TimestampType))
     transactionDF.show()
       
    
    
    val distanceUdf = udf(Utils.getDistance _)

    val processedDF = transactionDF.join(broadcast(customerAgeDF), Seq("cc_num"))
      .withColumn("distance", lit(round(distanceUdf($"lat", $"long", $"merch_lat", $"merch_long"), 2)))
      .select("cc_num", "trans_num", "trans_time", "category", "merchant", "amt", "merch_lat", "merch_long", "distance", "age", "is_fraud")

    processedDF.cache()

    val fraudDF = processedDF.filter($"is_fraud" === 1)
    val nonFraudDF = processedDF.filter($"is_fraud" === 0)

    /* Save fraud transaction data to fraud_transaction cassandra table*/
    fraudDF.write
      .format("org.apache.spark.sql.cassandra")
      .mode("append")
      .options(Map("keyspace" -> CassandraConfig.keyspace, "table" -> CassandraConfig.fraudTransactionTable))
      .save()

    /* Save non fraud transaction data to non_fraud_transaction cassandra table*/
    nonFraudDF.write
      .format("org.apache.spark.sql.cassandra")
      .mode("append")
      .options(Map("keyspace" -> CassandraConfig.keyspace, "table" -> CassandraConfig.nonFraudTransactionTable))
      .save()


  }

}
