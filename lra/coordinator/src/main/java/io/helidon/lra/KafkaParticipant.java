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
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.opentracing.Traced;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Compensated;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Completed;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

public class KafkaParticipant extends Participant {
    private static final Logger LOGGER = Logger.getLogger(KafkaParticipant.class.getName());

    // The Kafka specific config parsed from URIs
    private KafkaChannelConfig completeConfig, compensateConfig, afterLRAConfig, statusConfig, forgetConfig;
    // Since topics are fairly inexpensive in Kafka, currently the requirement for KafkaParticipants is a topic per channel.
    // Ie there is not option to filter on HELIDONLRAOPERATION as there is with AQ selector option, and therefore
    //  there is  a listener per bootstrapservers plus topic (bootstrapservers + "-" + topic)
    private static Map<String, KafkaReplyListener> bootstrapserversToListenerMap = new ConcurrentHashMap<>();

    /**
     * Parse URIs into KafkaChannelConfig
     * Eg. messaging://helidon-kafka/?channel=kafkacompletechannel&bootstrap.servers=kafkacompletechannel&topic=order-events&group.id=lra-example
     * Add KafkaReplyListener to map keyed on completeConfig.bootstrapservers + "-" + completeConfig.topic
     */
    public void init() {
            if (!isInitialized) {
                if (!isConfigInitialized) {
                    parseURIToConfig(getCompleteURI(), completeConfig = new KafkaChannelConfig());
                    parseURIToConfig(getCompensateURI(), compensateConfig = new KafkaChannelConfig());
                    parseURIToConfig(getAfterURI(), afterLRAConfig = new KafkaChannelConfig());
                    parseURIToConfig(getStatusURI(), statusConfig = new KafkaChannelConfig());
                    parseURIToConfig(getForgetURI(), forgetConfig = new KafkaChannelConfig());
                    isConfigInitialized = true;
                    LOGGER.info("Configuration initialized");
                }
                addAndStartListener(completeConfig, false, COMPLETESEND);
                addAndStartListener(compensateConfig, false, COMPENSATESEND);
                addAndStartListener(afterLRAConfig, true, AFTERLRASEND);
                addAndStartListener(statusConfig, true, STATUSSEND);
                addAndStartListener(forgetConfig, true, FORGETSEND);
                isInitialized = true;
                LOGGER.info("Reply listeners started");
            }
    }

    private void addAndStartListener(KafkaChannelConfig config, boolean isOptional, String operation) {
        if(isOptional && (config.bootstrapservers == null || config.bootstrapservers.equals(""))) return;
        if (!bootstrapserversToListenerMap.containsKey(config.bootstrapservers + "-" + config.sendtotopic)) {
            KafkaReplyListener listener = new KafkaReplyListener(config, operation);
            bootstrapserversToListenerMap.put(config.bootstrapservers + "-" + config.sendtotopic, listener);
            new Thread(listener).start();
//            executorService.submit(listener);
        }
    }

    private void parseURIToConfig(URI uri, KafkaChannelConfig channelConfig) {
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
                    channelConfig.sendtotopic = paramValue; //todo this is temp until we pass the outgoing/reply channel info into join
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
        if (isCancel) sendCompensate(lra);
        else sendComplete(lra);
    }

    @Traced
    @Counted
    private void sendComplete(LRA lra) {
        sendMessageAndWaitForReply(lra, completeConfig, COMPLETESEND);
        setParticipantStatus(Completed);
    }

    @Traced
    @Counted
    private void sendCompensate(LRA lra) {
        sendMessageAndWaitForReply(lra, compensateConfig, COMPENSATESEND);
        setParticipantStatus(Compensated);
    }

    @Traced
    @Counted
    @Override
    void sendStatus(LRA lra, URI statusURI) {
        sendMessageAndWaitForReply(lra, statusConfig, STATUSSEND); //todo setParticipantStatus(participantStatus);
    }

    @Traced
    @Counted
    @Override
    void sendAfterLRA(LRA lra) {
        String outcome = sendMessageAndWaitForReply(lra, afterLRAConfig, AFTERLRASEND);
        if (outcome.equals("success") )setAfterLRASuccessfullyCalledIfEnlisted();
    }

    @Traced
    @Counted
    @Override
    boolean sendForget(LRA lra) {
        logParticipantMessageWithTypeAndDepth("KafkaParticipant.sendForget", lra.nestedDepth);
        sendMessageAndWaitForReply(lra, forgetConfig, COMPLETESEND);
        setForgotten();
        return true;
    }

    /**
     * Sends specified operation message to participant and waits for reply that is received by the KafkaReplyListener.
     * @param lra The LRA whose id we will send as header.
     * @param channelConfig The configuration for the topic, etc. we are sending to.
     * @param operation The operation being sent. This will also be passed as a header.
     */
    private String sendMessageAndWaitForReply(LRA lra, KafkaChannelConfig channelConfig, String operation) {
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
        LOGGER.info("Sending " + operation + " to topic:" + channelConfig.sendtotopic +
                " lra.lraId:" + lra.lraId+ " bootstrapservers:" + channelConfig.bootstrapservers);
        List<Header> headers = Arrays.asList(new RecordHeader(LRA_HTTP_CONTEXT_HEADER, lra.lraId.getBytes()));
        ProducerRecord<String, String> record =
                new ProducerRecord<>(channelConfig.sendtotopic, null, HELIDONLRAOPERATION, operation, headers); //todo partition
        producer.send(record);
        producer.close();
        KafkaReplyListener replyListener = bootstrapserversToListenerMap.get(channelConfig.bootstrapservers + "-" + channelConfig.sendtotopic);
        replyListener.lraIDToReplyStatusMap.put(lra.lraId, operation);
//        replyListener.lraIDToReplyStatusMap.put(lra.lraId, operation);
        String replyStatus;
        do {
            replyStatus = replyListener.lraIDToReplyStatusMap.get(lra.lraId); //it will equal COMPLETESUCCESS or COMPLETEFAILURE eg
//            replyStatus = replyListener.lraIDToReplyStatusMap.get(lra.lraId); //it will equal COMPLETESUCCESS or COMPLETEFAILURE eg
            LOGGER.info("Still waiting for reply from " + operation + " to topic:" + channelConfig.sendtotopic +
                    " lra.lraId:" + lra.lraId+ " bootstrapservers:" + channelConfig.bootstrapservers + " current replyStatus:" + replyStatus);
            try {
                Thread.sleep(1000 * 1); //todo wait/notify
            } catch (InterruptedException e) {
                LOGGER.warning("InterruptedException waiting for reply from topic:" + channelConfig.sendtotopic + " operation:" + operation);
            }
        } while (replyStatus.equals(operation)); //todo timeout (add backoff and/or config retries) and return "failure" or exception
        LOGGER.info("Returning as replyListener for operation:" + operation + " lraID is " + replyStatus);
        return "success";
    }

}
