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
package io.helidon.messaging.connectors.jms.shim;

import java.io.Serializable;

import jakarta.jms.BytesMessage;
import jakarta.jms.ConnectionMetaData;
import jakarta.jms.Destination;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.StreamMessage;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;

/**
 * Exposes Jakarta API, delegates to javax API.
 */
class JakartaContext implements JMSContext {
    private final javax.jms.JMSContext delegate;

    JakartaContext(javax.jms.JMSContext delegate) {
        this.delegate = delegate;
    }

    @Override
    public JMSContext createContext(int sessionMode) {
        return JakartaJms.create(delegate.createContext(sessionMode));
    }

    @Override
    public JMSProducer createProducer() {
        return JakartaJms.create(delegate.createProducer());
    }

    @Override
    public String getClientID() {
        return delegate.getClientID();
    }

    @Override
    public void setClientID(String clientID) {
        delegate.setClientID(clientID);
    }

    @Override
    public ConnectionMetaData getMetaData() {
        return JakartaJms.create(delegate.getMetaData());
    }

    @Override
    public ExceptionListener getExceptionListener() {
        return JakartaJms.create(delegate.getExceptionListener());
    }

    @Override
    public void setExceptionListener(ExceptionListener listener) {
        delegate.setExceptionListener(JavaxJms.create(listener));
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public void setAutoStart(boolean autoStart) {
        delegate.setAutoStart(autoStart);
    }

    @Override
    public boolean getAutoStart() {
        return delegate.getAutoStart();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public BytesMessage createBytesMessage() {
        return JakartaJms.create(delegate.createBytesMessage());
    }

    @Override
    public MapMessage createMapMessage() {
        return JakartaJms.create(delegate.createMapMessage());
    }

    @Override
    public Message createMessage() {
        return JakartaJms.create(delegate.createMessage());
    }

    @Override
    public ObjectMessage createObjectMessage() {
        return JakartaJms.create(delegate.createObjectMessage());
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable object) {
        return JakartaJms.create(delegate.createObjectMessage(object));
    }

    @Override
    public StreamMessage createStreamMessage() {
        return JakartaJms.create(delegate.createStreamMessage());
    }

    @Override
    public TextMessage createTextMessage() {
        return JakartaJms.create(delegate.createTextMessage());
    }

    @Override
    public TextMessage createTextMessage(String text) {
        return JakartaJms.create(delegate.createTextMessage(text));
    }

    @Override
    public boolean getTransacted() {
        return delegate.getTransacted();
    }

    @Override
    public int getSessionMode() {
        return delegate.getSessionMode();
    }

    @Override
    public void commit() {
        delegate.commit();
    }

    @Override
    public void rollback() {
        delegate.rollback();
    }

    @Override
    public void recover() {
        delegate.recover();
    }

    @Override
    public JMSConsumer createConsumer(Destination destination) {
        return JakartaJms.create(delegate.createConsumer(ShimUtil.destination(destination)));
    }

    @Override
    public JMSConsumer createConsumer(Destination destination, String messageSelector) {
        return JakartaJms.create(delegate.createConsumer(ShimUtil.destination(destination), messageSelector));
    }

    @Override
    public JMSConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal) {
        return JakartaJms.create(delegate.createConsumer(ShimUtil.destination(destination), messageSelector, noLocal));
    }

    @Override
    public Queue createQueue(String queueName) {
        return JakartaJms.create(delegate.createQueue(queueName));
    }

    @Override
    public Topic createTopic(String topicName) {
        return JakartaJms.create(delegate.createTopic(topicName));
    }

    @Override
    public JMSConsumer createDurableConsumer(Topic topic, String name) {
        return JakartaJms.create(delegate.createDurableConsumer(ShimUtil.topic(topic), name));
    }

    @Override
    public JMSConsumer createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal) {
        return JakartaJms.create(delegate.createDurableConsumer(ShimUtil.topic(topic), name, messageSelector, noLocal));
    }

    @Override
    public JMSConsumer createSharedDurableConsumer(Topic topic, String name) {
        return JakartaJms.create(delegate.createSharedDurableConsumer(ShimUtil.topic(topic), name));
    }

    @Override
    public JMSConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) {
        return JakartaJms.create(delegate.createSharedDurableConsumer(ShimUtil.topic(topic), name, messageSelector));
    }

    @Override
    public JMSConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) {
        return JakartaJms.create(delegate.createSharedConsumer(ShimUtil.topic(topic), sharedSubscriptionName));
    }

    @Override
    public JMSConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName, String messageSelector) {
        return JakartaJms.create(delegate.createSharedConsumer(ShimUtil.topic(topic),
                                                               sharedSubscriptionName,
                                                               messageSelector));
    }

    @Override
    public QueueBrowser createBrowser(Queue queue) {
        return JakartaJms.create(delegate.createBrowser(ShimUtil.queue(queue)));
    }

    @Override
    public QueueBrowser createBrowser(Queue queue, String messageSelector) {
        return JakartaJms.create(delegate.createBrowser(ShimUtil.queue(queue), messageSelector));
    }

    @Override
    public TemporaryQueue createTemporaryQueue() {
        return JakartaJms.create(delegate.createTemporaryQueue());
    }

    @Override
    public TemporaryTopic createTemporaryTopic() {
        return JakartaJms.create(delegate.createTemporaryTopic());
    }

    @Override
    public void unsubscribe(String name) {
        delegate.unsubscribe(name);
    }

    @Override
    public void acknowledge() {
        delegate.acknowledge();
    }
}
