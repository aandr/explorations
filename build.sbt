name := "explorations"

version := "1.0"

scalaVersion := "2.12.3"

resolvers += "confluent" at "http://packages.confluent.io/maven/"
resolvers += Resolver.bintrayRepo("cakesolutions", "maven")

lazy val akkaVersion = "2.5.4"
lazy val akkaHttpVersion = "10.0.10"

lazy val kafkaVersion = "0.11.0.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-actor" % akkaVersion,
  "com.typesafe.akka"         %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka"         %% "akka-stream" % akkaVersion,
  "com.typesafe.akka"         %% "akka-stream-kafka" % "0.17",

  "com.typesafe.akka"         %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka"         %% "akka-http-testkit" % akkaHttpVersion,
  "com.typesafe.akka"         %% "akka-http-spray-json" % akkaHttpVersion,

  "org.apache.kafka"          % "kafka-clients" % kafkaVersion,
  "org.apache.kafka"          % "kafka-streams" % kafkaVersion,
  "log4j"                     % "log4j" % "1.2.17",

  "org.slf4j"                 % "slf4j-simple" % "1.7.25" % "test",
  "org.scalatest"             %% "scalatest" % "3.0.1" % "test",
  "net.cakesolutions"         %% "scala-kafka-client-testkit" % "0.11.0.0" % "test",
)
