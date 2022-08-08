/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
package io.helidon.messaging.connectors.jms.shim;

import jakarta.jms.BytesMessage;
import jakarta.jms.CompletionListener;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionConsumer;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ConnectionMetaData;
import jakarta.jms.Destination;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.ServerSession;
import jakarta.jms.ServerSessionPool;
import jakarta.jms.Session;
import jakarta.jms.StreamMessage;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import jakarta.jms.TopicSubscriber;

/**
 * Main shim entry point, allows wrapping javax types to jakarta types.
 */
public final class JakartaJms {
    private JakartaJms() {
    }

    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static BytesMessage create(javax.jms.BytesMessage delegate) {
        return new JakartaByteMessage(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static CompletionListener create(javax.jms.CompletionListener delegate) {
        return new JakartaCompletionListener(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static Connection create(javax.jms.Connection delegate) {
        return new JakartaConnection(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static ConnectionConsumer create(javax.jms.ConnectionConsumer delegate) {
        return new JakartaConnectionConsumer(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static ConnectionFactory create(javax.jms.ConnectionFactory delegate) {
        return new JakartaConnectionFactory(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static ConnectionMetaData create(javax.jms.ConnectionMetaData delegate) {
        return new JakartaConnectionMetaData(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static JMSConsumer create(javax.jms.JMSConsumer delegate) {
        return new JakartaConsumer(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static JMSContext create(javax.jms.JMSContext delegate) {
        return new JakartaContext(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static Destination create(javax.jms.Destination delegate) {
        return new JakartaDestination<>(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static ExceptionListener create(javax.jms.ExceptionListener delegate) {
        return new JakartaExceptionListener(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static MapMessage create(javax.jms.MapMessage delegate) {
        return new JakartaMapMessage(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static Message create(javax.jms.Message delegate) {
        if (delegate instanceof javax.jms.TextMessage) {
            return create((javax.jms.TextMessage) delegate);
        }
        if (delegate instanceof javax.jms.MapMessage) {
            return create((javax.jms.MapMessage) delegate);
        }
        if (delegate instanceof javax.jms.BytesMessage) {
            return create((javax.jms.BytesMessage) delegate);
        }
        if (delegate instanceof javax.jms.StreamMessage) {
            return create((javax.jms.StreamMessage) delegate);
        }
        if (delegate instanceof javax.jms.ObjectMessage) {
            return create((javax.jms.ObjectMessage) delegate);
        }
        if (delegate == null) {
            return null;
        }
        return new JakartaMessage(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static MessageConsumer create(javax.jms.MessageConsumer delegate) {
        return new JakartaMessageConsumer(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static MessageListener create(javax.jms.MessageListener delegate) {
        return new JakartaMessageListener(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static MessageProducer create(javax.jms.MessageProducer delegate) {
        return new JakartaMessageProducer(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static ObjectMessage create(javax.jms.ObjectMessage delegate) {
        return new JakartaObjectMessage(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static JMSProducer create(javax.jms.JMSProducer delegate) {
        return new JakartaProducer(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static Queue create(javax.jms.Queue delegate) {
        return new JakartaQueue(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static QueueBrowser create(javax.jms.QueueBrowser delegate) {
        return new JakartaQueueBrowser(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static Session create(javax.jms.Session delegate) {
        return new JakartaSession(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static ServerSessionPool create(javax.jms.ServerSessionPool delegate) {
        return new JakartaSessionPool(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static ServerSession create(javax.jms.ServerSession delegate) {
        return new JakartaServerSession(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static StreamMessage create(javax.jms.StreamMessage delegate) {
        return new JakartaStreamMessage(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static TemporaryQueue create(javax.jms.TemporaryQueue delegate) {
        return new JakartaTemporaryQueue(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static TemporaryTopic create(javax.jms.TemporaryTopic delegate) {
        return new JakartaTemporaryTopic(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static TextMessage create(javax.jms.TextMessage delegate) {
        return new JakartaTextMessage(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static Topic create(javax.jms.Topic delegate) {
        return new JakartaTopic(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static TopicSubscriber create(javax.jms.TopicSubscriber delegate) {
        return new JakartaTopicSubscriber(delegate);
    }
}
