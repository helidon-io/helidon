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

import oracle.jms.AQjmsConstants;
import oracle.jms.AQjmsFactory;
import oracle.jms.AQjmsSession;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.opentracing.Traced;

import javax.jms.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

public class AQParticipant extends Participant {
    private static final Logger LOGGER = Logger.getLogger(AQParticipant.class.getName());
    private AQChannelConfig completeConfig, compensateConfig, afterLRAConfig, forgetConfig;
    private final static String UCP_POOLDATASOURCE = "oracle.ucp.jdbc.PoolDataSource.";
    private static PoolDataSource aqParticipantDB;
    private TopicConnectionFactory topicConnectionFactory;
    private TopicConnection topicConnection;
    private static Map<String, AQReplyListener> destinationAndTypeToListenerMap = new ConcurrentHashMap<>();
    // Unlike REST or Kafka we don't do retries and so we keep track of where we are in the state model with this
    protected String lastActionTakenOrReceived = INIT;

    static {
        System.setProperty("oracle.jdbc.fanEnabled", "false"); //silence benign message re ONS
    }

    String getParticipantType() {
        return "AQ";
    }

    public void init() {
        try {
            if (!isInitialized) {
                initConn();
                if (!isConfigInitialized) {
                    parseURIToConfig(getCompleteURI(), completeConfig = new AQChannelConfig());
                    parseURIToConfig(getCompensateURI(), compensateConfig = new AQChannelConfig());
                    parseURIToConfig(getAfterURI(), afterLRAConfig = new AQChannelConfig());
                    parseURIToConfig(getForgetURI(), forgetConfig = new AQChannelConfig());
                    isConfigInitialized = true;
                }
                addAndStartListener(completeConfig, false, COMPLETESEND);
                addAndStartListener(compensateConfig, false, COMPENSATESEND);
                addAndStartListener(afterLRAConfig, true, AFTERLRASEND);
                addAndStartListener(forgetConfig, true, FORGETSEND);
                isInitialized = true;
                LOGGER.info("Reply listeners started");
            }
        } catch (SQLException | JMSException ex) {
            LOGGER.warning("Exception during init of " + this + ":" + ex);
        }
    }

    private void addAndStartListener(AQChannelConfig config, boolean isOptional, String operation) throws JMSException {
        if(isOptional && (completeConfig.destination == null || completeConfig.destination.equals(""))) return;
        if (!destinationAndTypeToListenerMap.containsKey(completeConfig.destination + "-" + completeConfig.type)) {
            AQReplyListener listener = new AQReplyListener(aqParticipantDB, config, operation);
            destinationAndTypeToListenerMap.put(completeConfig.destination + "-" + completeConfig.type, listener);
            new Thread(listener).start();
//            executorService.submit(listener);
        }
    }

    private void parseURIToConfig(URI uri, AQChannelConfig channelConfig) {
        if (uri == null) return;
        List<NameValuePair> params = URLEncodedUtils.parse(uri, Charset.forName("UTF-8"));
        String paramName, paramValue;
        for (NameValuePair param : params) {
            paramName = param.getName();
            paramValue = param.getValue();
            LOGGER.fine(paramName + " : " + paramValue);
            switch (paramName) {
                case "owner":
                    channelConfig.owner = paramValue;
                    break;
                case "type":
                    channelConfig.type = paramValue;
                    break;
                case "destination":
                    channelConfig.destination = paramValue;
                    break;
                default:
            }
        }
    }

    /**
     * Parse and init the connector if necessary and the participant varibles from the URL
     * User's are required to configure the client and coordinator connector names to match.
     * Rather than have an additional requirement that the underlying datasource for the AQ connector also have the same name, we look it up here...
     *
     * @throws JMSException from AQ creation
     * @throws SQLException from datasource creation
     */
    private void initConn() throws JMSException, SQLException { //todo service is not in ready state until this completes/inits at the very least
        Config config = ConfigProvider.getConfig();
        String messagingConnectorDatasourceName = config.getValue("mp.messaging.connector.helidon-aq.data-source", String.class);
        LOGGER.info("messagingConnectorDatasourceName:" + messagingConnectorDatasourceName);
        String connectionFactoryClassName = config.getValue(UCP_POOLDATASOURCE + messagingConnectorDatasourceName + ".connectionFactoryClassName", String.class);
        String url = config.getValue(UCP_POOLDATASOURCE + messagingConnectorDatasourceName + ".URL", String.class);
        String user = config.getValue(UCP_POOLDATASOURCE + messagingConnectorDatasourceName + ".user", String.class);
        String pw = config.getValue(UCP_POOLDATASOURCE + messagingConnectorDatasourceName + ".password", String.class);
        if (aqParticipantDB == null) {
            aqParticipantDB = PoolDataSourceFactory.getPoolDataSource();
            aqParticipantDB.setConnectionFactoryClassName(connectionFactoryClassName);
            aqParticipantDB.setURL(url);
            aqParticipantDB.setUser(user);
            aqParticipantDB.setPassword(pw);
        } //todo handle javax.sql.DataSource. and NoSuchElementException
        //todo set properties of/on the datasource (ie inactiveConnectionTimeout etc) or devise a way to reuse UCP
        if (topicConnectionFactory == null)
            topicConnectionFactory = AQjmsFactory.getTopicConnectionFactory(aqParticipantDB);
        if (topicConnection == null) topicConnection = topicConnectionFactory.createTopicConnection();
    }


    @Override
        //break into cancel and complete methods for better metrics and tracing
    void sendCompleteOrCancel(LRA lra, boolean isCancel) {
        URI endpointURI = isCancel ? getCompensateURI() : getCompleteURI();
        if (lastActionTakenOrReceived.equals(INIT)) { //todo this is wrong, eg last action could be COMPLETEREPLY with failed status
            LOGGER.info("AQParticipant.sendCompleteOrCancel endpointURI:" + endpointURI);
            init();
            if(isCancel) sendCompensate(lra);
            else sendComplete(lra);
        }
    }

    @Traced
    @Counted
    private void sendComplete(LRA lra) {
        send(COMPLETESEND, lra, completeConfig);
        lastActionTakenOrReceived = COMPLETESEND;
    }

    @Traced
    @Counted
    private void sendCompensate(LRA lra) {
        send(COMPLETESEND, lra, completeConfig);
        lastActionTakenOrReceived = COMPLETESEND;
    }


    @Traced
    @Counted
    @Override
    void sendStatus(LRA lra, URI statusURI) {
        //no-op for AQ as there is guaranteed message delivery
    }

    @Traced
    @Counted
    @Override
    boolean sendForget(LRA lra) {
        if (!lastActionTakenOrReceived.equals(FORGETSEND)) {
            LOGGER.info("AQParticipant.sendForget endpointURI:" + getForgetURI());
            init();
            send(FORGETSEND, lra, null);
            lastActionTakenOrReceived = FORGETSEND;
        }
        return true;
    }

    @Traced
    @Counted
    @Override
    void sendAfterLRA(LRA lra) {
        if (!lastActionTakenOrReceived.equals(AFTERLRASEND)) {
            LOGGER.info("AQParticipant.sendForget endpointURI:" + getForgetURI());
            init();
            String outcome = send(AFTERLRASEND, lra, null);
            lastActionTakenOrReceived = AFTERLRASEND;
            if (outcome.equals("success") )setAfterLRASuccessfullyCalledIfEnlisted();
            logParticipantMessageWithTypeAndDepth("AQParticipant afterLRA finished outcome:" + outcome, lra.nestedDepth);
        }
    }

    private String send(String operation, LRA lra, AQChannelConfig channelConfig) {
        try (TopicSession session = topicConnection.createTopicSession(true, Session.CLIENT_ACKNOWLEDGE)) {
            LOGGER.info("session:" + session + " destination:" + channelConfig.destination + " operation:" + operation);
            Topic topic = ((AQjmsSession) session).getTopic(aqParticipantDB.getUser(), channelConfig.destination);
            LOGGER.info("sendEvent topic:" + topic);
            TextMessage objmsg = session.createTextMessage();
            //todo same for queue
            TopicPublisher publisher = session.createPublisher(topic);
            objmsg.setStringProperty(HELIDONLRAOPERATION, operation);
            objmsg.setStringProperty(LRA_HTTP_CONTEXT_HEADER, lra.lraId);
            objmsg.setIntProperty("Id", 1);
            objmsg.setIntProperty("Priority", 2);
            publisher.publish(topic, objmsg, DeliveryMode.PERSISTENT, 2, AQjmsConstants.EXPIRATION_NEVER);
            session.commit();
            LOGGER.info("session:" + session + " destination:" + channelConfig.destination + " message sent. Waiting for reply...");
        } catch (JMSException e) {
            LOGGER.info(e.getMessage());
        }
        AQReplyListener replyListener = destinationAndTypeToListenerMap.get(channelConfig.destination + "-" + channelConfig.type);
        replyListener.lraIDToReplyStatusMap.put("testlraid", operation);
//        replyListener.lraIDToReplyStatusMap.put(lra.lraId, operation);
        String replyStatus;
        do {
//            replyStatus = replyListener.lraIDToReplyStatusMap.get(lra.lraId); //it will equal COMPLETESUCCESS or COMPLETEFAILURE eg
            replyStatus = replyListener.lraIDToReplyStatusMap.get("testlraid"); //it will equal COMPLETESUCCESS or COMPLETEFAILURE eg
            LOGGER.info("no reply received for lra, replyStatus " + replyStatus);
            try {
                Thread.sleep(1000 * 1); //todo wait/notify
            } catch (InterruptedException e) {
                LOGGER.warning("InterruptedException waiting for reply from destination:" + channelConfig.destination + " operation:" + operation);
            }
        } while (replyStatus.equals(operation)); //todo throw exception if timeout
        LOGGER.info("Returning as replyListener for lraID is " + replyStatus);
        return "success";
    }


}
