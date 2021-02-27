package io.helidon.lra;

import oracle.jms.AQjmsConsumer;
import oracle.jms.AQjmsFactory;
import oracle.jms.AQjmsSession;
import oracle.jms.AQjmsTopicReceiver;

import javax.jms.*;
import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static io.helidon.lra.Participant.HELIDONLRAOPERATION;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

public class AQReplyListener implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(AQReplyListener.class.getName());
    private final String operation;
    private final String type;
    private final String destination;
    private final String owner;
    Map<String, String> lraIDToReplyStatusMap = new ConcurrentHashMap<>();
    QueueConnectionFactory queueConnectionFactory;
    TopicConnectionFactory topicConnectionFactory;

    public AQReplyListener(DataSource aqParticipantDB, AQChannelConfig channelConfig, String operation) throws JMSException {
        //todo attempt to listen to both queue and topic - generally one will fail but this prevents us from forcing the customer to configure which we should use
        this.operation = operation;
        this.type = "queue"; // channelConfig.type; todo for AQ we need to either have it configured our try both topic and queue in order to support topic-to-queue
        this.destination = "LRAREPLYQUEUE"; // + "REPLY" channelConfig.destination; todo get from config
        this.owner = "ORDERUSER";
        if(type.equals("topic") )topicConnectionFactory = AQjmsFactory.getTopicConnectionFactory(aqParticipantDB);
        else queueConnectionFactory = AQjmsFactory.getQueueConnectionFactory(aqParticipantDB);
        LOGGER.info("ReplyListener created for operation:" + operation + " channelConfig:" + channelConfig);
    }

    @Override
    public void run() {
        boolean done = false;
        while (!done) {
            if (type.equals("queue")) {
                try (QueueConnection qconn = queueConnectionFactory.createQueueConnection()) {
                    QueueSession qsess = qconn.createQueueSession(true, Session.CLIENT_ACKNOWLEDGE);
                    qconn.start();
                    Queue queue = ((AQjmsSession) qsess).getQueue(owner, destination);
                    AQjmsConsumer consumer = (AQjmsConsumer) qsess.createConsumer(queue);
                    LOGGER.info("Listening for replies from " + operation +
                            " operations on destination:" + destination + " owner:" + owner + " type:" + type);
                    Message message = consumer.receive(-1);
//       todo             String lraHelidonOperation = message.getStringProperty(HELIDONLRAOPERATION);
                    String lraId = message.getStringProperty(LRA_HTTP_CONTEXT_HEADER);
                    LOGGER.info("Received reply for operation:" + operation +
                            " operations on destination:" + destination + " owner:" + owner + " type:" + type + " lraId:" + lraId + " about to commit...");
                    qsess.commit();
//     todo               lraIDToReplyStatusMap.put("testlraid", lraHelidonOperation);
                    lraIDToReplyStatusMap.put("testlraid", "TESTREPLYFROM" + operation + "OPERATION");
                } catch (JMSException e) {
                    LOGGER.warning("JMSException during receive:" + e);
                }
            } else { // topic
                try (TopicConnection topicConn = topicConnectionFactory.createTopicConnection()) {
                    TopicSession topicSession = topicConn.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
//                    TopicSubscriber topicSubscriber = topicSession.createSubscriber(destination);
//                    topicConn.start();
//                    Message message = (TextMessage) topicSubscriber.receive();
//                    System.out.println("Message received: " + message.getText());
                } catch (JMSException e) {
                    LOGGER.warning("JMSException during receive:" + e);
                }
            }
        }
    }
}

