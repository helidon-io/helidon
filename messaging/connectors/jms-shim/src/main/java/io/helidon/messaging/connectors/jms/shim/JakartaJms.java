/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

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
        if (delegate == null) return null;
        return new JakartaByteMessage(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static CompletionListener create(javax.jms.CompletionListener delegate) {
        if (delegate == null) return null;
        return new JakartaCompletionListener(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static Connection create(javax.jms.Connection delegate) {
        if (delegate == null) return null;
        return new JakartaConnection(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static ConnectionConsumer create(javax.jms.ConnectionConsumer delegate) {
        if (delegate == null) return null;
        return new JakartaConnectionConsumer(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static ConnectionFactory create(javax.jms.ConnectionFactory delegate) {
        if (delegate == null) return null;
        return new JakartaConnectionFactory(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static ConnectionMetaData create(javax.jms.ConnectionMetaData delegate) {
        if (delegate == null) return null;
        return new JakartaConnectionMetaData(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static JMSConsumer create(javax.jms.JMSConsumer delegate) {
        if (delegate == null) return null;
        return new JakartaConsumer(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static JMSContext create(javax.jms.JMSContext delegate) {
        if (delegate == null) return null;
        return new JakartaContext(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static Destination create(javax.jms.Destination delegate) {
        if (delegate == null) return null;
        return new JakartaDestination<>(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static ExceptionListener create(javax.jms.ExceptionListener delegate) {
        if (delegate == null) return null;
        return new JakartaExceptionListener(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static MapMessage create(javax.jms.MapMessage delegate) {
        if (delegate == null) return null;
        return new JakartaMapMessage(delegate);
    }

    /**
     * Convenience method for shimming various javax JMS classes.
     *
     * @param obj to be shimmed or just typed
     * @param expectedType expected type to shim to
     * @return typed or shimmed object
     * @param <T> expected type to shim to
     */
    public static <T> T resolve(Object obj, Class<T> expectedType) {
        if (expectedType.isAssignableFrom(obj.getClass())) {
            return (T) obj;
        }
        Map<Class<?>, Function<Object, T>> conversionMap = Map.of(
                ConnectionFactory.class, o -> (T) JakartaJms.create((javax.jms.ConnectionFactory) o),
                Destination.class, o -> (T) JakartaJms.create((javax.jms.Destination) o)
        );
        return Optional.ofNullable(conversionMap.get(expectedType))
                .map(r -> r.apply(obj))
                .orElseThrow(() -> new IllegalStateException("Unexpected type of connection factory: " + obj.getClass()));
    }


    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static Message create(javax.jms.Message delegate) {
        if (delegate instanceof javax.jms.TextMessage textMessage) {
            return create(textMessage);
        }
        if (delegate instanceof javax.jms.MapMessage mapMessage) {
            return create(mapMessage);
        }
        if (delegate instanceof javax.jms.BytesMessage bytesMessage) {
            return create(bytesMessage);
        }
        if (delegate instanceof javax.jms.StreamMessage streamMessage) {
            return create(streamMessage);
        }
        if (delegate instanceof javax.jms.ObjectMessage objectMessage) {
            return create(objectMessage);
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
        if (delegate == null) return null;
        return new JakartaMessageConsumer(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static MessageListener create(javax.jms.MessageListener delegate) {
        if (delegate == null) return null;
        return new JakartaMessageListener(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static MessageProducer create(javax.jms.MessageProducer delegate) {
        if (delegate == null) return null;
        return new JakartaMessageProducer(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static ObjectMessage create(javax.jms.ObjectMessage delegate) {
        if (delegate == null) return null;
        return new JakartaObjectMessage(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static JMSProducer create(javax.jms.JMSProducer delegate) {
        if (delegate == null) return null;
        return new JakartaProducer(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static Queue create(javax.jms.Queue delegate) {
        if (delegate == null) return null;
        return new JakartaQueue(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static QueueBrowser create(javax.jms.QueueBrowser delegate) {
        if (delegate == null) return null;
        return new JakartaQueueBrowser(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static Session create(javax.jms.Session delegate) {
        if (delegate == null) return null;
        return new JakartaSession(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static ServerSessionPool create(javax.jms.ServerSessionPool delegate) {
        if (delegate == null) return null;
        return new JakartaSessionPool(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static ServerSession create(javax.jms.ServerSession delegate) {
        if (delegate == null) return null;
        return new JakartaServerSession(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static StreamMessage create(javax.jms.StreamMessage delegate) {
        if (delegate == null) return null;
        return new JakartaStreamMessage(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static TemporaryQueue create(javax.jms.TemporaryQueue delegate) {
        if (delegate == null) return null;
        return new JakartaTemporaryQueue(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static TemporaryTopic create(javax.jms.TemporaryTopic delegate) {
        if (delegate == null) return null;
        return new JakartaTemporaryTopic(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static TextMessage create(javax.jms.TextMessage delegate) {
        if (delegate == null) return null;
        return new JakartaTextMessage(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static Topic create(javax.jms.Topic delegate) {
        if (delegate == null) return null;
        return new JakartaTopic(delegate);
    }
    /**
     * Create a jakarta wrapper for the provided javax instance.
     * @param delegate javax namespace instance
     * @return shimmed jakarta namespace instance
     */
    public static TopicSubscriber create(javax.jms.TopicSubscriber delegate) {
        if (delegate == null) return null;
        return new JakartaTopicSubscriber(delegate);
    }
}
