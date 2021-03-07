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
import io.helidon.messaging.connectors.jms.JmsMessage;
import io.helidon.messaging.connectors.kafka.KafkaMessage;
import oracle.AQ.AQMessage;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.eclipse.microprofile.reactive.messaging.Message;

import javax.jms.JMSException;
import javax.ws.rs.core.*;
import java.util.*;
import java.util.logging.Logger;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

public class MessagingRequestContext {
    private static final Logger LOGGER = Logger.getLogger(MessagingRequestContext.class.getName());
    Map properties = new HashMap<>();
    MultivaluedMap<String, String> multivaluedMap = new MultivaluedHashMap<String, String>();
    Map<String, String> messagePropertiesMap = new HashMap<>();

    public MessagingRequestContext(Message message) {
        if (message instanceof AqMessage) {
//            javax.jms.Message jmsMessage = ((AqMessage) message).getJmsMessage();
//            messagePropertiesMap.forEach((propertyName, value1) -> {
//                Object value = value1;
//                try {
//                    jmsMessage.setStringProperty(propertyName, "" + value);
//                    LOGGER.info("set AqMessage MessageProperties for reply property:" + propertyName + " value:" + value);
//                } catch (JMSException e) {
//                    LOGGER.warning("JMSException in setJMSMessageProperties:" + e);
//                }
//            });
//            try {
//                jmsMessage.setStringProperty(LRA_HTTP_CONTEXT_HEADER, lraId);
//            } catch (JMSException e) {
//                LOGGER.warning("JMSException in setJMSMessageProperties for LRA_HTTP_CONTEXT_HEADER:" + e);
//            }
        } else if (message instanceof KafkaMessage) {
            KafkaMessage kafkaMessage = (KafkaMessage) message;
            Header header = kafkaMessage.getHeaders().lastHeader(LRA_HTTP_CONTEXT_HEADER);
            LOGGER.info("incoming LRA_HTTP_CONTEXT_HEADER header:" + header);
            if (header != null) {
                LOGGER.info("incoming LRA_HTTP_CONTEXT_HEADER header:" + new String(header.value()));
                addMessageProperty(LRA_HTTP_CONTEXT_HEADER, new String(header.value()));
            }
        }
    }

    Object getProperty(String var1) {
        return properties.get(var1);
    }

    void setProperty(String var1, Object var2) {
        properties.put(var1, var2);

    }

    void removeProperty(String var1) {
        properties.remove(var1);
    }

    MultivaluedMap<String, String> getHeaders() {
        return multivaluedMap;
    }

    void removeHeader(Object key) {
        multivaluedMap.remove(key);
//        messagePropertiesMap.remove(key);
    }

    void add(String key, String value) {
        multivaluedMap.add(key, value);
//        messagePropertiesMap.add(key, value);
    }

    void addMessageProperty(String key, String value) {
        LOGGER.info("addMessageProperty key = " + key + ", value = " + value);
        messagePropertiesMap.put(key, value);
    }


    /**
     * Set during outgoing in order to propagate lraID
     *
     * @param message
     */
    void setMessageProperties(Object message) {
        if (message instanceof JmsMessage) {
            javax.jms.Message jmsMessage = ((JmsMessage) message).getJmsMessage();
            messagePropertiesMap.forEach((propertyName, value1) -> {
                Object value = value1;
                try {
                    jmsMessage.setStringProperty(propertyName, "" + value);
                    LOGGER.info("set JmsMessage MessageProperties for reply property:" + propertyName + " value:" + value);
                } catch (JMSException e) {
                    LOGGER.warning("JMSException in setJMSMessageProperties:" + e);
                }
            });
        } else if (message instanceof KafkaMessage) {
            KafkaMessage kafkaMessage = (KafkaMessage) message;
            messagePropertiesMap.forEach((propertyName, value) -> {
                LOGGER.info("set KafkaMessage MessageProperties for reply property:" + propertyName + " value:" + value);
                kafkaMessage.getHeaders().add(propertyName, ((String) value).getBytes());
                LOGGER.info("set Kafka MessageProperties for reply property:" + propertyName + " value:" + value);
            });
        }
    }

    void abortWith(Response var1) {
        LOGGER.info("MessageRequestContext abortWith:" + var1);
    }

}
