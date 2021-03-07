package io.helidon.lra;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static io.helidon.lra.Participant.HELIDONLRAOPERATION;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

public class KafkaReplyListener implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(KafkaReplyListener.class.getName());
    private final String operation;
    private final String bootstrapservers;
    private final String topic;
    private final String groupid;
    Map<String, String> lraIDToReplyStatusMap = new ConcurrentHashMap<>();

    public KafkaReplyListener(KafkaChannelConfig channelConfig, String operation)  {
        this.operation = operation;
        this.bootstrapservers = channelConfig.bootstrapservers;
        this.topic = channelConfig.sendtotopic + "-reply"; //todo get "replyfromtopic" for outgoing channel config once it's added to join
        this.groupid = channelConfig.groupid;
        LOGGER.info("KafkReplyListener created for operation:" + operation + " channelConfig:" + channelConfig);
    }

    @Override
    public void run() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapservers);
        props.put("group.id", "lra"); //todo use channelConfig.groupid (can this be empty or "lra" if not specified?)
        //todo make these configurable at some point as well...
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("session.timeout.ms", "30000");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        KafkaConsumer<String, String> consumer = new KafkaConsumer <String, String>(props);
        LOGGER.info("consumer subscribing to topic:" + topic + " on bootstrapservers:" + bootstrapservers + " for operation:" + operation );
        consumer.subscribe(Arrays.asList(topic));
        boolean isReplyRecordNotFoundYet = true;
        while (isReplyRecordNotFoundYet) {
            ConsumerRecords<String, String> records = consumer.poll(100);
            for (ConsumerRecord<String, String> record : records) { //todo check value
                LOGGER.info("offset:" + record.offset() + " key:" + record.key() + " value:" + record.value());
                Header lraidheader = record.headers().lastHeader(LRA_HTTP_CONTEXT_HEADER);
                Header lraOperationHeader = record.headers().lastHeader(HELIDONLRAOPERATION);
                LOGGER.info("lraIdHeaders:" + new String(lraidheader.value()) + " lraOperationHeader:" + lraOperationHeader);
                lraIDToReplyStatusMap.put(new String(lraidheader.value()), new String(lraOperationHeader.value()));
            }
        }
        LOGGER.severe("consumer subscribing to topic:" + topic + "on bootstrapservers:" + bootstrapservers + " for operation:" + operation + " exiting");
    }
}
