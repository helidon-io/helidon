/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.StreamMessage;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import jakarta.jms.TopicSubscriber;

import static io.helidon.messaging.connectors.jms.shim.ShimUtil.call;

/**
 * Exposes Jakarta API, delegates to javax API.
 *
 * @param <T> Type of the javax delegate
 */
public class JakartaSession<T extends javax.jms.Session> implements Session, JakartaWrapper<T> {
    private final T delegate;

    JakartaSession(T delegate) {
        this.delegate = delegate;
    }

    @Override
    public BytesMessage createBytesMessage() throws JMSException {
        return JakartaJms.create(call(delegate::createBytesMessage));
    }

    @Override
    public MapMessage createMapMessage() throws JMSException {
        return JakartaJms.create(call(delegate::createMapMessage));
    }

    @Override
    public Message createMessage() throws JMSException {
        return JakartaJms.create(call(delegate::createMessage));
    }

    @Override
    public ObjectMessage createObjectMessage() throws JMSException {
        return JakartaJms.create((javax.jms.ObjectMessage) call(delegate::createObjectMessage));
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable object) throws JMSException {
        return JakartaJms.create(call(() -> delegate.createObjectMessage(object)));
    }

    @Override
    public StreamMessage createStreamMessage() throws JMSException {
        return JakartaJms.create(call(delegate::createStreamMessage));
    }

    @Override
    public TextMessage createTextMessage() throws JMSException {
        return JakartaJms.create((javax.jms.TextMessage) call(delegate::createTextMessage));
    }

    @Override
    public TextMessage createTextMessage(String text) throws JMSException {
        return JakartaJms.create((javax.jms.TextMessage) call(() -> delegate.createTextMessage(text)));
    }

    @Override
    public boolean getTransacted() throws JMSException {
        return call(delegate::getTransacted);
    }

    @Override
    public int getAcknowledgeMode() throws JMSException {
        return call(delegate::getAcknowledgeMode);
    }

    @Override
    public void commit() throws JMSException {
        ShimUtil.run(delegate::commit);
    }

    @Override
    public void rollback() throws JMSException {
        ShimUtil.run(delegate::rollback);
    }

    @Override
    public void close() throws JMSException {
        ShimUtil.run(delegate::close);
    }

    @Override
    public void recover() throws JMSException {
        ShimUtil.run(delegate::recover);
    }

    @Override
    public MessageListener getMessageListener() throws JMSException {
        return JakartaJms.create(call(delegate::getMessageListener));
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException {
        ShimUtil.run(() -> delegate.setMessageListener(JavaxJms.create(listener)));
    }

    @Override
    public void run() {
        delegate.run();
    }

    @Override
    public MessageProducer createProducer(Destination destination) throws JMSException {
        return JakartaJms.create(call(() -> delegate.createProducer(ShimUtil.destination(destination))));
    }

    @Override
    public MessageConsumer createConsumer(Destination destination) throws JMSException {
        return JakartaJms.create(call(() -> delegate.createConsumer(ShimUtil.destination(destination))));
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector) throws JMSException {
        return JakartaJms.create(call(() -> delegate.createConsumer(ShimUtil.destination(destination),
                                                                    messageSelector)));
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal) throws JMSException {
        return JakartaJms.create(call(() -> delegate.createConsumer(ShimUtil.destination(destination),
                                                                    messageSelector,
                                                                    noLocal)));
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) throws JMSException {
        return JakartaJms.create(call(() -> delegate.createSharedConsumer(ShimUtil.topic(topic),
                                                                          sharedSubscriptionName)));
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName, String messageSelector)
            throws JMSException {
        return JakartaJms.create(call(() -> delegate.createSharedConsumer(ShimUtil.topic(topic),
                                                                          sharedSubscriptionName,
                                                                          messageSelector)));
    }

    @Override
    public Queue createQueue(String queueName) throws JMSException {
        return JakartaJms.create(call(() -> delegate.createQueue(queueName)));
    }

    @Override
    public Topic createTopic(String topicName) throws JMSException {
        return JakartaJms.create(call(() -> delegate.createTopic(topicName)));
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException {
        return JakartaJms.create(call(() -> delegate.createDurableSubscriber(ShimUtil.topic(topic), name)));
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal)
            throws JMSException {
        return JakartaJms.create(call(() -> delegate.createDurableSubscriber(ShimUtil.topic(topic),
                                                                             name,
                                                                             messageSelector,
                                                                             noLocal)));
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String name) throws JMSException {
        return JakartaJms.create(call(() -> delegate.createDurableConsumer(ShimUtil.topic(topic),
                                                                           name)));
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal)
            throws JMSException {
        return JakartaJms.create(call(() -> delegate.createDurableConsumer(ShimUtil.topic(topic),
                                                                           name,
                                                                           messageSelector,
                                                                           noLocal)));
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name) throws JMSException {
        return JakartaJms.create(call(() -> delegate.createSharedDurableConsumer(ShimUtil.topic(topic),
                                                                                 name)));
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) throws JMSException {
        return JakartaJms.create(call(() -> delegate.createSharedDurableConsumer(ShimUtil.topic(topic),
                                                                                 name,
                                                                                 messageSelector)));
    }

    @Override
    public QueueBrowser createBrowser(Queue queue) throws JMSException {
        return JakartaJms.create(call(() -> delegate.createBrowser(ShimUtil.queue(queue))));
    }

    @Override
    public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException {
        return JakartaJms.create(call(() -> delegate.createBrowser(ShimUtil.queue(queue), messageSelector)));
    }

    @Override
    public TemporaryQueue createTemporaryQueue() throws JMSException {
        return JakartaJms.create(call(delegate::createTemporaryQueue));
    }

    @Override
    public TemporaryTopic createTemporaryTopic() throws JMSException {
        return JakartaJms.create(call(delegate::createTemporaryTopic));
    }

    @Override
    public void unsubscribe(String name) throws JMSException {
        ShimUtil.run(() -> delegate.unsubscribe(name));
    }

    @Override
    public T unwrap() {
        return delegate;
    }
}
