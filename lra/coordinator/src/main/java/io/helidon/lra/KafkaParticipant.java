/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.lra;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Compensated;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Completed;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

//todo allow queueing of requests/replies as this is rudimentary and inefficient currently both being inline
public class KafkaParticipant extends Participant {
    private static final Logger LOGGER = Logger.getLogger(KafkaParticipant.class.getName());

    // The Kafka specific config parsed from URIs
    private ChannelConfig completeConfig, compensateConfig, afterLRAConfig, statusConfig, forgetConfig;

    /**
     * Parse URIs into ChannelConfig
     * Eg. messaging://helidon-kafka/?channel=kafkacompletechannel&bootstrap.servers=kafkacompletechannel&topic=order-events&group.id=lra-example
     */
    public  void init(){
        parseURIToConfig(getCompleteURI(), completeConfig = new ChannelConfig());
        parseURIToConfig(getCompensateURI(), compensateConfig = new ChannelConfig());
        parseURIToConfig(getAfterURI(), afterLRAConfig = new ChannelConfig());
        parseURIToConfig(getStatusURI(), statusConfig = new ChannelConfig());
        parseURIToConfig(getForgetURI(), forgetConfig = new ChannelConfig());
    }

    private void parseURIToConfig(URI uri, ChannelConfig channelConfig) {
        if (uri == null) return;
        List<NameValuePair> params = URLEncodedUtils.parse(uri, Charset.forName("UTF-8"));
        String paramName, paramValue;
        for (NameValuePair param : params) {
            paramName = param.getName();
            paramValue = param.getValue();
            LOGGER.fine(paramName + " : " + paramValue);
            switch (paramName)
            {
                case "bootstrap.servers":
                    channelConfig.bootstrapservers = paramValue;
                    break;
                case "topic":
                    channelConfig.topic = paramValue;
                    break;
                case "groupid":
                    channelConfig.groupid = paramValue;
                    break;
                default:
            }
        }
    }

    String getParticipantType() {
        return "Kafka";
    }

    @Override
    void sendCompleteOrCancel(LRA lra, boolean isCancel) {
        sendMessage(lra, isCancel?compensateConfig:completeConfig, false, "COMPLETESEND");
    }

    @Override
    void sendStatus(LRA lra, URI statusURI) {
        logParticipantMessageWithTypeAndDepth("kafka participant.sendstatus", lra.nestedDepth);
        sendMessage(lra, statusConfig, false, "COMPLETESEND");
    }

    @Override
    void sendAfterLRA(LRA lra) {
        logParticipantMessageWithTypeAndDepth("kafka participant.sendAfterLRA", lra.nestedDepth);
        sendMessage(lra, afterLRAConfig, false, "COMPLETESEND");
    }

    @Override
    boolean sendForget(LRA lra) {
        logParticipantMessageWithTypeAndDepth("kafka participant.sendForget", lra.nestedDepth);
        sendMessage(lra, forgetConfig, false, "COMPLETESEND");
        return true;
    }

    /**
     * Is cancel unused
     * @param lra
     * @param channelConfig
     * @param isCancel
     * @param operation
     */
    private void sendMessage(LRA lra, ChannelConfig channelConfig, boolean isCancel, String operation) {
        Properties props = new Properties(); //todo get all of this from config
        props.put("bootstrap.servers", channelConfig.bootstrapservers);
        //todo appropriate values...
        props.put("acks", "all");
        props.put("retries", 0);
        props.put("batch.size", 16384);
        props.put("linger.ms", 1);
        props.put("buffer.memory", 33554432);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        Producer<String, String> producer = new KafkaProducer<String, String>(props);
        LOGGER.info("Sending to topic:" + channelConfig.topic + " lra.lraId:" + lra.lraId+ " bootstrapservers:" + channelConfig.bootstrapservers);
        List<Header> headers = Arrays.asList(new RecordHeader(LRA_HTTP_CONTEXT_HEADER, lra.lraId.getBytes()));
        ProducerRecord<String, String> record = new ProducerRecord<>(channelConfig.topic, null, HELIDONLRAOPERATION, operation, headers); //todo partition
        producer.close();
        receive(channelConfig, isCancel);
    }

    /**
     * Receive reply for request as appropriate
     * @param isCancel if is a termination call is this complete/cancel or compensate/close
     */
    void receive(ChannelConfig channelConfig, boolean isCancel)  {
        Properties props = new Properties(); //todo get all of this from config
        props.put("bootstrap.servers", channelConfig.bootstrapservers);
        //todo appropriate values...
        props.put("group.id", "test");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("session.timeout.ms", "30000");
        props.put("key.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer");
        KafkaConsumer<String, String> consumer = new KafkaConsumer
                <String, String>(props);
        consumer.subscribe(Arrays.asList(channelConfig.topic + "reply"));
        boolean isReplyRecordNotFoundYet = true;
        while (isReplyRecordNotFoundYet) {
            ConsumerRecords<String, String> records = consumer.poll(100);
            for (ConsumerRecord<String, String> record : records) { //todo check value
                LOGGER.info("offset:" + record.offset() + " key:" + record.key() + " value:" + record.value());
                Iterable<Header> headers = record.headers().headers(LRA_HTTP_CONTEXT_HEADER);
                setParticipantStatus(isCancel ? Compensated : Completed);
                isReplyRecordNotFoundYet = false;
            }
        }
    }

    private class ChannelConfig {
        String bootstrapservers, topic, groupid;
    }
}
