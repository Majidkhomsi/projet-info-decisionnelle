import org.apache.spark.sql.{SparkSession, DataFrame, SaveMode}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.jdbc.{JdbcDialect, JdbcDialects, JdbcType}

object YelpETL {
  def main(args: Array[String]): Unit = {
    // 🔥 Création de la session Spark avec une configuration mémoire optimisée
    val spark = SparkSession.builder()
      .appName("YelpETL")
      .master("local[*]")
      .config("spark.sql.shuffle.partitions", "8") // Optimisation des partitions
      .config("spark.executor.memory", "8g") // RAM pour chaque tâche Spark
      .config("spark.driver.memory", "6g") // RAM pour le driver
      .config("spark.sql.autoBroadcastJoinThreshold", "-1") // Désactiver les broadcast joins
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")
    JdbcDialects.registerDialect(OracleDialect)

    // --- Connexions BD ---
    val pgUrl = "jdbc:postgresql://stendhal.iem:5432/tpid2020"
    val pgProps = new java.util.Properties()
    pgProps.setProperty("user", "tpid")
    pgProps.setProperty("password", "tpid")

    val oracleUrl = "jdbc:oracle:thin:@stendhal.iem:1521/enss2024"
    val oracleProps = new java.util.Properties()
    oracleProps.setProperty("user", "ir618188")
    oracleProps.setProperty("password", "ir618188")

    try {
      // --- (A) EXTRACT ---
      println("Lecture des fichiers JSON Yelp...")
      val dfBusinessRaw = spark.read.option("multiline", "false")
        .json("/home4/ir618188/Desktop/info_decis/yelp_academic_dataset_business.json")

      val dfCheckinRaw = spark.read.option("multiline", "false")
        .json("/home4/ir618188/Desktop/info_decis/yelp_academic_dataset_checkin.json")

      val dfUserRaw = spark.read.jdbc(pgUrl, "(SELECT * FROM yelp.\"user\" ORDER BY user_id LIMIT 2000) as sub", pgProps)

      // --- (B) TRANSFORM : Création des dimensions ---
      println("Transformation des dimensions...")
      val dfBusiness = dfBusinessRaw.select(
        "business_id", "name", "city", "state", "postal_code", "latitude",
        "longitude", "stars", "review_count", "is_open", "categories"
      )

      val dfUser = dfUserRaw.select(
        "user_id", "name", "review_count", "average_stars",
        "fans", "cool", "funny", "useful", "yelping_since"
      )

      // --- (C) LOAD Dimensions dans Oracle ---
      println("Écriture de DIM_BUSINESS en cours...")
      dfBusiness.write.mode(SaveMode.Append)
        .option("batchsize", 5000)
        .jdbc(oracleUrl, "DIM_BUSINESS", oracleProps)
      println("✅ DIM_BUSINESS terminée.")

      println("Écriture de DIM_USER en cours...")
      dfUser.write.mode(SaveMode.Append)
        .option("batchsize", 5000)
        .jdbc(oracleUrl, "DIM_USER", oracleProps)
      println("✅ DIM_USER terminée.")

      // --- Récupération des Clés Dimensionnelles ---
      val dfDimBusinessWithKey = spark.read.jdbc(oracleUrl, "DIM_BUSINESS", oracleProps)
        .select("BUSINESS_KEY", "BUSINESS_ID")

      val dfDimUserWithKey = spark.read.jdbc(oracleUrl, "DIM_USER", oracleProps)
        .select("USER_KEY", "USER_ID")

      // --- (D) FACT TABLE : CHECKIN ---
      println("Transformation de FACT_CHECKIN...")
      val dfCheckin = dfCheckinRaw
        .select(col("business_id"), split(col("date"), ",\\s*").as("dates_array"))
        .withColumn("nb_checkins", size(col("dates_array")))
        .select("business_id", "nb_checkins")

      val dfCheckinJoined = dfCheckin.join(dfDimBusinessWithKey, Seq("business_id"), "left")
        .select(col("BUSINESS_KEY"), col("nb_checkins"))

      println("Écriture de FACT_CHECKIN en cours...")
      dfCheckinJoined.write.mode(SaveMode.Append)
        .option("batchsize", 5000)
        .jdbc(oracleUrl, "FACT_CHECKIN", oracleProps)
      println("✅ FACT_CHECKIN terminée.")

      // --- (E) FACT TABLE : REVIEW (Optimisé) ---
      println("Chargement des reviews (limité à 2000 lignes)...")
      val dfReviewRaw = spark.read.jdbc(pgUrl, 
        """(SELECT review_id, user_id, business_id, stars, useful, funny, cool, date 
           FROM yelp."review" 
           WHERE date >= '2020-01-01' 
           LIMIT 2000) as sub""", pgProps)
        .repartition(8) // Partitionnement pour éviter OOM

      println(s"Nombre total de reviews chargées : ${dfReviewRaw.count()}")

      println("Jointure avec les dimensions...")
      val dfReviewJoined = dfReviewRaw
        .join(dfDimBusinessWithKey, Seq("business_id"), "left")
        .join(dfDimUserWithKey, Seq("user_id"), "left")
        .select(
          col("BUSINESS_KEY"), col("USER_KEY"), col("stars"),
          col("funny"), col("useful"), col("cool"), col("review_id"), col("date")
        )
        .distinct() // 🔹 Évite les doublons après la jointure
        .repartition(8) // Limiter la mémoire utilisée

      println(s"Nombre total de reviews après transformation : ${dfReviewJoined.count()}")

      println("Écriture de FACT_REVIEW en cours...")
      dfReviewJoined.coalesce(1) // 🔹 Réduire le nombre de fichiers de sortie
        .write
        .mode(SaveMode.Append)
        .option("batchsize", 5000)
        .option("isolationLevel", "READ_COMMITTED") // 🔹 Éviter les conflits d'écriture
        .option("numPartitions", 8) // 🔹 Optimiser la parallélisation
        .jdbc(oracleUrl, "FACT_REVIEW", oracleProps)
      println("✅ FACT_REVIEW terminée.")

      println("🚀 ETL terminé avec succès !")

    } catch {
      case e: Exception =>
        println("⚠ ERREUR dans l'ETL : " + e.getMessage)
        e.printStackTrace()
    } finally {
      spark.stop()
    }
  }
}

//------------------------------------------------------------------
// 🛠️ Dialect Oracle pour une meilleure compatibilité JDBC
//------------------------------------------------------------------
object OracleDialect extends JdbcDialect {
  override def canHandle(url: String): Boolean = url.startsWith("jdbc:oracle")
  override def getJDBCType(dt: DataType): Option[JdbcType] = dt match {
    case BooleanType   => Some(JdbcType("NUMBER(1)", java.sql.Types.INTEGER))
    case IntegerType   => Some(JdbcType("NUMBER(10)", java.sql.Types.INTEGER))
    case LongType      => Some(JdbcType("NUMBER(19)", java.sql.Types.BIGINT))
    case FloatType     => Some(JdbcType("NUMBER(19,4)", java.sql.Types.FLOAT))
    case DoubleType    => Some(JdbcType("NUMBER(19,4)", java.sql.Types.DOUBLE))
    case ByteType      => Some(JdbcType("NUMBER(3)", java.sql.Types.SMALLINT))
    case ShortType     => Some(JdbcType("NUMBER(5)", java.sql.Types.SMALLINT))
    case StringType    => Some(JdbcType("VARCHAR2(4000)", java.sql.Types.VARCHAR))
    case DateType      => Some(JdbcType("DATE", java.sql.Types.DATE))
    case TimestampType => Some(JdbcType("TIMESTAMP", java.sql.Types.TIMESTAMP))
    case _ => None
  }
}
