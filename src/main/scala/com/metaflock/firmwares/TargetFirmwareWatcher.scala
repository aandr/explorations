package com.metaflock.firmwares

import java.util
import java.util.concurrent.ConcurrentHashMap

import akka.actor.{Actor, ActorSystem, Props}
import akka.dispatch.Futures
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.Sink
import com.metaflock.amon.KafkaHelper
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.{ClientRequest, ClientResponse, KafkaClient, RequestCompletionHandler}
import org.apache.kafka.common.Node
import org.apache.kafka.common.requests.AbstractRequest
import org.apache.kafka.common.serialization.{LongDeserializer, Serdes, StringDeserializer}
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.kstream.KStreamBuilder
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.immutable.{HashMap, TreeMap}
import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._


class TargetFirmwareWatcher extends App {
  implicit val timeout = Timeout(5 seconds) // needed for `?` below

  var deviceFirmwares: TreeMap[String, java.lang.Long] = _
  //  var deviceFirmwares = new ConcurrentHashMap[String, java.lang.Long]()
  var numUpdates = 0

  def runWithStreams(): Unit = {
    deviceFirmwares = TreeMap[String, java.lang.Long]()
    //     deviceFirmwares = new ConcurrentHashMap[String, java.lang.Long]()

    val builder = new KStreamBuilder
    builder.stream(Serdes.String(), Serdes.Long(), "device_target_fw")
      .foreach { (deviceId: String, firmware: java.lang.Long) =>
        numUpdates += 1
        //          deviceFirmwares.put(deviceId, firmware)
        deviceFirmwares += (deviceId -> firmware)
      }

    val streams = new KafkaStreams(builder, KafkaHelper.getStreamsConfig("test"));
    streams.start()
  }

  def runWithClient(): Unit = {
    deviceFirmwares = TreeMap[String, java.lang.Long]()

    val client = new KafkaConsumer[String, java.lang.Long](KafkaHelper.getConsumerConfig())
    client.subscribe(List("device_target_fw").asJava)

    while (true) {
      val records = client.poll(100)
      records.iterator().forEachRemaining { rec =>
        numUpdates += 1
        deviceFirmwares += (rec.key() -> rec.value())
      }
    }
  }

  def runWithAkkaStreams(): Unit = {
    var devices = TreeMap[String, Long]()

    implicit val system = ActorSystem("Hello")
    implicit val materializer = ActorMaterializer()

    val consumerSettings = ConsumerSettings(system, keyDeserializer = new StringDeserializer, valueDeserializer = new LongDeserializer)
      .withBootstrapServers("localhost:9092").withGroupId("group-akka").withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

    val done = Consumer.plainSource(consumerSettings, Subscriptions.topics("device_target_fw"))
      .map { msg =>
        numUpdates += 1
        devices += (msg.key() -> msg.value())
      }
      .runWith(Sink.ignore)
  }

  def runWithActor(): Unit = {
    implicit val system = ActorSystem("Hello")
    implicit val materializer = ActorMaterializer()

    implicit val timeout = Timeout(5 seconds) // needed for `?` below

    val actorRef = system.actorOf(Props[DevicesActor])
    val consumerSettings = ConsumerSettings(system, keyDeserializer = new StringDeserializer, valueDeserializer = new LongDeserializer)
      .withBootstrapServers("localhost:9092").withGroupId("group-akka").withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

    val done = Consumer.plainSource(consumerSettings, Subscriptions.topics("device_target_fw"))
      .map {
        msg =>
          numUpdates += 1
          ActorMsg(msg.key(), msg.value())
      }
      .runWith(Sink.actorRef(actorRef, "Complete"))
  }
}

case class ActorMsg(val deviceId: String, val firmware: Long)

class DevicesActor extends Actor {
  var deviceFirmwares = HashMap[String, Long]()
  var numUpdates = 0

  override def receive = {
    case ActorMsg(deviceId, firmware) =>
      deviceFirmwares += (deviceId -> firmware)
    case "NumUpdates" =>
      sender() ! numUpdates
    case "Hello" =>
      sender() ! "ok"
  }
}

object EventeSourcedActor {
  var numUpdates = 0
}


class EventeSourcedActor extends Actor {
  val consumerSettings = ConsumerSettings(context.system, keyDeserializer = new StringDeserializer, valueDeserializer = new LongDeserializer)
    .withBootstrapServers("localhost:9092").withGroupId("group-akka").withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  implicit val materializer = ActorMaterializer()

  override def preStart(): Unit = {
    val done = Consumer.plainSource(consumerSettings, Subscriptions.topics("device_target_fw"))
      .map { msg =>
        self ! ActorMsg(msg.key(), msg.value())
      }
      .runWith(Sink.ignore)
    println("prestart done")
  }

  var devices = TreeMap.empty[String, Long]
  var numUpdates = 0

  override def receive: Receive = {
    case ActorMsg(deviceId, firmware) =>
      devices += (deviceId -> firmware)
      EventeSourcedActor.numUpdates += 1
    case "Hello" =>
      println("GOt hello")
  }
}
