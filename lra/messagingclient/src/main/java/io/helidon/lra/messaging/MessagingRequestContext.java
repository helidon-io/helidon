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
package io.helidon.lra.messaging;

import io.helidon.messaging.connectors.aq.AqMessage;
import io.helidon.messaging.connectors.kafka.KafkaMessage;
import oracle.AQ.AQMessage;
import org.apache.kafka.common.header.Headers;
import org.eclipse.microprofile.reactive.messaging.Message;

import javax.jms.JMSException;
import javax.ws.rs.core.*;
import java.util.*;
import java.util.logging.Logger;

public class MessagingRequestContext {
    private static final Logger LOGGER = Logger.getLogger(MessagingRequestContext.class.getName());
    Map properties = new HashMap<>();
    MultivaluedMap<String, String> multivaluedMap = new MultivaluedHashMap<String, String>();
    MultivaluedMap<String, String> messagePropertiesMap = new MultivaluedHashMap<String, String>();

    public MessagingRequestContext(Message message) {
//        if (message instanceof AqMessage) {
//            try {
//                AqMessage aqMessage = (AqMessage) message;
//                javax.jms.Message jmsMessage = aqMessage.getJmsMessage();
//                Enumeration srcProperties = jmsMessage.getPropertyNames();
//                while (srcProperties.hasMoreElements()) {
//                    String propertyName = (String)srcProperties.nextElement ();
//                    String value = "" + jmsMessage.getObjectProperty(propertyName);
//                    messagePropertiesMap.add(propertyName, value);
//                }
//            } catch (JMSException e) {
//                e.printStackTrace();
//            }
//        } else if (message instanceof KafkaMessage) {
//            KafkaMessage kafkaMessage = (KafkaMessage)message;
//            Headers headers = kafkaMessage.getHeaders();
//            //todo messagePropertiesMap.add...
//
//        } else {
//            LOGGER.warning("message type not supported (not of type AQ or Kakfa):" + message);
//        }
    }

    Object getProperty(String var1) {
        return properties.get(var1);
    }

    void setProperty(String var1, Object var2){
        properties.put(var1, var2);
    }

    void removeProperty(String var1){
        properties.remove(var1);
    }

    MultivaluedMap<String, String> getHeaders(){
        return multivaluedMap;
    }

    void removeHeader(Object key) {
        multivaluedMap.remove(key);
        messagePropertiesMap.remove(key);
    }

    void add(String key, String value) {
        multivaluedMap.add(key, value);
        messagePropertiesMap.add(key, value);
    }

    void addMessageProperty(String key, String value) {
        messagePropertiesMap.add(key, value);
    }


    /**
     * Set during outgoing in order to propagate lraID
     * @param message
     */
    void setMessageProperties(Object  message)  {
        if (message instanceof AqMessage) {
            javax.jms.Message jmsMessage = ((AqMessage) message).getJmsMessage();
//            AQMessage jmsMessage = ((AQMessage) message); // .getJmsMessage();
            for (Map.Entry<String, List<String>> entry : messagePropertiesMap.entrySet()) {
                String propertyName = entry.getKey();
                Object value = entry.getValue();
                try {
                    jmsMessage.setStringProperty(propertyName, "" + value);
//                    jmsMessage.setMessageProperty(propertyName, value);
                    LOGGER.info("set JMS MessageProperties for reply property:" + propertyName + " value:" + value);
                } catch (JMSException e) {
                    LOGGER.warning("JMSException in setJMSMessageProperties:" + e);
                }
            }
        } else if (message instanceof KafkaMessage) {
            KafkaMessage kafkaMessage = (KafkaMessage) message;
            for (Map.Entry<String, List<String>> entry : messagePropertiesMap.entrySet()) {
                String propertyName = entry.getKey();
                Object value = entry.getValue();
                    kafkaMessage.getHeaders().add(propertyName, ((String)value).getBytes());
                    LOGGER.info("set Kafka MessageProperties for reply property:" + propertyName + " value:" + value);
            }
        }
    }

    void abortWith(Response var1){
        LOGGER.info("MessageRequestContext abortWith:" + var1);
    }

}
