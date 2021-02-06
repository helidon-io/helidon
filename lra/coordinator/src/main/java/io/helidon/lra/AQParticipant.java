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

import io.opentracing.Tracer;
import oracle.jms.AQjmsConstants;
import oracle.jms.AQjmsConsumer;
import oracle.jms.AQjmsFactory;
import oracle.jms.AQjmsSession;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.inject.Inject;
import javax.jms.*;
import java.net.URI;
import java.sql.SQLException;

public class AQParticipant extends Participant {

    PoolDataSource aqParticipantDB; //todo should be shared
    TopicConnectionFactory q_cf;
    TopicConnection q_conn;

    @Inject
    private Tracer tracer;

    //todo these values will come from parsing of joinlra
    String queueOwner = "orderuser";
    String selector = "";
    String compensateQueue = "COMPENSATEQUEUE"; //or topic as the case may be
    String completeQueue = "COMPLETEQUEUE"; //or topic as the case may be
    boolean isNotSentYet = true;

    public AQParticipant() {
        try { //todo get from config
            initConn(); //todo needs to prime/ping
        } catch (SQLException | JMSException ex) { //todo add init flag
            ex.printStackTrace();
        }
    }

    private void initConn() throws JMSException, SQLException { //todo service is not in ready state until this completes/inits at the very least
        Config config = ConfigProvider.getConfig();
        config.getPropertyNames().forEach(s -> System.out.println("AQParticipant.config s:" + s));
        if (aqParticipantDB == null) {
            aqParticipantDB = PoolDataSourceFactory.getPoolDataSource();
            aqParticipantDB.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
            aqParticipantDB.setURL("jdbc:oracle:thin:@orderdb_tp?TNS_ADMIN=/Users/pparkins/Downloads/Wallet_ORDERANDINVENTORYDB");
            aqParticipantDB.setUser("orderuser");
            aqParticipantDB.setPassword("Welcome12345");
        }
        if (q_cf == null) q_cf = AQjmsFactory.getTopicConnectionFactory(aqParticipantDB);
        if (q_conn == null) q_conn = q_cf.createTopicConnection();
    }

    @Override
    boolean sendForget(LRA lra, boolean areAllThatNeedToBeForgottenForgotten) {
        return true;
    }

    @Override
    void sendAfterLRA(LRA lra) {

    }

    @Override
    void sendCompleteOrCancel(LRA lra, boolean isCancel) {
        URI endpointURI = isCancel ? getCompensateURI() : getCompleteURI();
        if (isNotSentYet) {
            System.out.println("AQParticipant.sendCompleteOrCancel endpointURI:" + endpointURI); //todo endpoint has slash ending ie "compensate/"
            sendEvent(lra, isCancel ? compensateQueue : completeQueue);
        }
    }

    @Override
    void sendStatus(LRA lra, URI statusURI) {

    }

    String sendEvent(LRA lra, String queueName) {
        TopicSession session = null;
        try {
            System.out.println("AQParticipant.sendEvent q_conn:" + q_conn);
            initConn();
            session = q_conn.createTopicSession(true, Session.CLIENT_ACKNOWLEDGE);
            //check for topic or queue
            Topic topic = ((AQjmsSession) session).getTopic(queueOwner, queueName);
            System.out.println("sendEvent topic:" + topic);
            TextMessage objmsg = session.createTextMessage();
            TopicPublisher publisher = session.createPublisher(topic);
            objmsg.setIntProperty("Id", 1);
            objmsg.setIntProperty("Priority", 2);
//            String jsonString = JsonUtils.writeValueAsString(insertedOrder);
            String jsonString = "{}";
            objmsg.setText(jsonString);
            objmsg.setJMSCorrelationID("" + 1);
            objmsg.setJMSPriority(2);
            publisher.publish(topic, objmsg, DeliveryMode.PERSISTENT, 2, AQjmsConstants.EXPIRATION_NEVER);
            session.commit();
            isNotSentYet = false;
            return topic.toString();
        } catch (Exception e) {
            System.out.println("sendEvent failed with exception:" + e +
                    " (will attempt rollback if session is not null) session:" + session);
            e.printStackTrace();
            if (session != null) {
                try {
                    session.rollback();
                } catch (JMSException e1) {
                    System.out.println("sendEvent session.rollback() failed:" + e1);
                } finally {
//                    throw e;
                }
//                    throw e;
            }
            return "" + e; //todo
        } finally {
            isNotSentYet = false;
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public void listenForMessages(String queueName) throws JMSException {
        QueueConnectionFactory q_cf = AQjmsFactory.getQueueConnectionFactory(aqParticipantDB);
        QueueSession qsess = null;
        QueueConnection qconn = null;
        AQjmsConsumer consumer = null;
        boolean done = false;
        while (!done) {
            try {
                if (qconn == null || qsess == null) {
                    qconn = q_cf.createQueueConnection();
                    qsess = qconn.createQueueSession(true, Session.CLIENT_ACKNOWLEDGE);
                    qconn.start();
                    Queue queue = ((AQjmsSession) qsess).getQueue(queueOwner, queueName);
                    consumer = (AQjmsConsumer) qsess.createConsumer(queue);
                }
                TextMessage textMessage = (TextMessage) consumer.receive(-1);
                String messageText = textMessage.getText();
//                MessageResponse messageResponse = JsonUtils.read(messageText, MessageResponse.class);
                qsess.commit();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exception in receiveMessages: " + e);
                qsess.rollback();
            }
        }
    }
}
