name := "YelpETL"

version := "1.0"

scalaVersion := "2.12.17"  // Compatible avec Spark 3.3.1 et 3.5.4

// Dépendances Spark et JDBC
libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.5.4",
  "org.apache.spark" %% "spark-sql"  % "3.5.4",
  "org.apache.spark" %% "spark-hive" % "3.5.4",  // (optionnel) si tu utilises Hive
  "org.postgresql" % "postgresql" % "42.6.0",
  "com.oracle.database.jdbc" % "ojdbc8" % "21.9.0.0"
)

// Options JVM pour optimiser la mémoire et Spark
javaOptions ++= Seq(
  "-Xmx16G",  // Augmenter la mémoire disponible (si possible)
  "-XX:+UseG1GC",  // Utiliser G1 Garbage Collector
  "-XX:InitiatingHeapOccupancyPercent=35",  // Optimisation du GC
  "-XX:+HeapDumpOnOutOfMemoryError",  // Dump mémoire en cas d'erreur
  "-XX:MaxMetaspaceSize=512m",
  "-Duser.timezone=UTC"  // Pour éviter des problèmes de fuseau horaire avec les dates
)

// Réduction des warnings inutiles
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")

// Augmenter le nombre de partitions pour les gros datasets
ThisBuild / Test / testOptions += Tests.Argument("-oF")
