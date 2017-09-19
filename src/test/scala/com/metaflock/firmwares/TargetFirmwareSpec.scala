package com.metaflock.firmwares

import java.time.Instant

import akka.actor
import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import cakesolutions.kafka.testkit.KafkaServer
import com.metaflock.amon.KafkaHelper
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.{LongSerializer, Serdes, StringSerializer}
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._

class TargetFirmwareSpec extends WordSpec with Matchers with Eventually {
  import Utils._

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(120, Seconds)), interval = scaled(Span(500, Millis)))



  def publish(): Unit = {
    val producer = new KafkaProducer[String, java.lang.Long](KafkaHelper.getProducerConfig())

    println("Publishing")
    0 until NUM_UPDATES map { n =>
      producer.send(new ProducerRecord[String, java.lang.Long]("device_target_fw", s"hello${n}", n.toLong))
    }
    0 until NUM_UPDATES map { n =>
      producer.send(new ProducerRecord[String, java.lang.Long]("device_target_fw", s"hello${n}", n.toLong * 2))
    }
    producer.flush()
    producer.close()
    println("Published")
  }

  "The TargetFwWatcher" should {

    "self-sourced actor" in {
      val kafkaServer = new KafkaServer(kafkaPort = 9092)
      kafkaServer.startup()
      val producer = new KafkaProducer[String, java.lang.Long](KafkaHelper.getProducerConfig())

      println("Publishing")
      0 until NUM_UPDATES map { n =>
        producer.send(new ProducerRecord[String, java.lang.Long]("device_target_fw", s"hello${n}", n.toLong))
      }
      0 until NUM_UPDATES map { n =>
        producer.send(new ProducerRecord[String, java.lang.Long]("device_target_fw", s"hello${n}", n.toLong * 2))
      }
      producer.flush()
      producer.close()

      println("Published")

      implicit val system = ActorSystem()
      implicit val materializer = ActorMaterializer()


      Utils.time {
        val ref = system.actorOf(Props[EventeSourcedActor])
        val ref2 = system.actorOf(Props[SecondActor], "actor2")
        implicit val timeout = Timeout(5 seconds)
        implicit val ec = system.dispatcher

        var n = 0

        eventually {
          val num = EventeSourcedActor.numUpdates
          println(num + " " + n)
          if (num > 0) {
            n += 1
          }
          num shouldEqual NUM_UPDATES * 2
        }
      }

      kafkaServer.close()

      Runtime.getRuntime.gc()
    }

    "watch all firmware changes with Akka Streams" in {
      val kafkaServer = new KafkaServer(kafkaPort = 9092)
      kafkaServer.startup()

      publish()

      val startTime = System.nanoTime()
      val watcher = new TargetFirmwareWatcher()

      Utils.time {
        new Thread({ () => watcher.runWithAkkaStreams() }).start()

        var n = 0

        eventually {
          println(watcher.numUpdates + " " + n)
          if (watcher.numUpdates > 0) {
            n += 1
          }
          watcher.numUpdates shouldEqual NUM_UPDATES * 2
        }

        kafkaServer.close()

      }
      Runtime.getRuntime.gc()
    }

    "watch all firmware changes with streams" in {
      val kafkaServer = new KafkaServer(kafkaPort = 9092)
      kafkaServer.startup()
      val producer = new KafkaProducer[String, java.lang.Long](KafkaHelper.getProducerConfig())

      println("Publishing")
      0 until NUM_UPDATES map { n =>
        producer.send(new ProducerRecord[String, java.lang.Long]("device_target_fw", s"hello${n}", n.toLong))
      }
      0 until NUM_UPDATES map { n =>
        producer.send(new ProducerRecord[String, java.lang.Long]("device_target_fw", s"hello${n}", n.toLong * 2))
      }
      producer.flush()
      producer.close()
      println("Published")

      val watcher = new TargetFirmwareWatcher()

      Utils.time {
        new Thread({ () => watcher.runWithStreams() }).start()

        var n = 0

        eventually {
          println(watcher.numUpdates + " " + n)
          if (watcher.numUpdates > 0) {
            n += 1
          }
          watcher.numUpdates shouldEqual NUM_UPDATES * 2
        }
      }

      kafkaServer.close()

      Runtime.getRuntime.gc()
    }

    "watch all firmware changes with Kafka Consumer" in {
      val kafkaServer = new KafkaServer(kafkaPort = 9092)
      kafkaServer.startup()
      val producer = new KafkaProducer[String, java.lang.Long](KafkaHelper.getProducerConfig())

      println("Publishing")
      0 until NUM_UPDATES map { n =>
        producer.send(new ProducerRecord[String, java.lang.Long]("device_target_fw", s"hello${n}", n.toLong))
      }
      0 until NUM_UPDATES map { n =>
        producer.send(new ProducerRecord[String, java.lang.Long]("device_target_fw", s"hello${n}", n.toLong * 2))
      }
      producer.flush()
      println("Published")

      val watcher = new TargetFirmwareWatcher()
      new Thread({ () => watcher.runWithClient() }).start()

      var n = 0

      eventually {
        println(watcher.numUpdates + " " + n)
        if (watcher.numUpdates > 0) {
          n += 1
        }
        watcher.numUpdates shouldEqual NUM_UPDATES * 2
      }

      kafkaServer.close()

      println(s"Consumer total: ${n / 2}")
      Runtime.getRuntime.gc()
    }

  }

}

object Utils {
  val NUM_UPDATES = 10000000

  def time[R](block: => R): R = {
    val t0 = System.nanoTime() / 1000000000
    val result = block // call-by-name
    val t1 = System.nanoTime() / 1000000000

    println("Elapsed time: " + (t1 - t0) + "s")

    println("Throughput: " + (NUM_UPDATES * 2 / (t1 - t0)) + " msg/s")
    result
  }
}