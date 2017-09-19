package com.metaflock.amon

import java.util.Properties

import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.{LongDeserializer, LongSerializer, StringDeserializer, StringSerializer}
import org.apache.kafka.streams.StreamsConfig

object KafkaHelper {

  def getConsumerProperties(appId: String): Properties = {
    val settings = new Properties()
    // Set a few key parameters
    settings.put(StreamsConfig.APPLICATION_ID_CONFIG, appId)
    settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    settings
  }

  def getStreamsConfig(appId: String) = new StreamsConfig(getConsumerProperties(appId))

  def getProducerConfig(): Properties = {
    val settings = new Properties()
    settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer])
    settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[LongSerializer])
    settings
  }

  def getConsumerConfig(): Properties = {
    val settings = new Properties()
    settings.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    settings.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer])
    settings.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[LongDeserializer])
    settings.put(ConsumerConfig.GROUP_ID_CONFIG, "group-consumer")
    settings.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    settings
  }


}
