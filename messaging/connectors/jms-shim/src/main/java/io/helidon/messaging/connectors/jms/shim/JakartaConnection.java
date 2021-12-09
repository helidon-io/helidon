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

import jakarta.jms.Connection;
import jakarta.jms.ConnectionConsumer;
import jakarta.jms.ConnectionMetaData;
import jakarta.jms.Destination;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;
import jakarta.jms.ServerSessionPool;
import jakarta.jms.Session;
import jakarta.jms.Topic;

import static io.helidon.messaging.connectors.jms.shim.ShimUtil.call;
import static io.helidon.messaging.connectors.jms.shim.ShimUtil.run;

/**
 * Exposes Jakarta API, delegates to javax API.
 */
class JakartaConnection implements Connection {
    private final javax.jms.Connection connection;

    JakartaConnection(javax.jms.Connection connection) {
        this.connection = connection;
    }

    @Override
    public Session createSession(boolean transacted, int acknowledgeMode) throws JMSException {
        return JakartaJms.create(call(() -> connection.createSession(transacted, acknowledgeMode)));
    }

    @Override
    public Session createSession(int sessionMode) throws JMSException {
        return JakartaJms.create(call(() -> connection.createSession(sessionMode)));
    }

    @Override
    public Session createSession() throws JMSException {
        return JakartaJms.create((javax.jms.Session) call(connection::createSession));
    }

    @Override
    public String getClientID() throws JMSException {
        return call(connection::getClientID);
    }

    @Override
    public void setClientID(String clientID) throws JMSException {
        run(() -> connection.setClientID(clientID));
    }

    @Override
    public ConnectionMetaData getMetaData() throws JMSException {
        return JakartaJms.create(call(connection::getMetaData));
    }

    @Override
    public ExceptionListener getExceptionListener() throws JMSException {
        return JakartaJms.create(call(connection::getExceptionListener));
    }

    @Override
    public void setExceptionListener(ExceptionListener listener) throws JMSException {
        run(() -> connection.setExceptionListener(JavaxJms.create(listener)));
    }

    @Override
    public void start() throws JMSException {
        run(connection::start);
    }

    @Override
    public void stop() throws JMSException {
        run(connection::stop);
    }

    @Override
    public void close() throws JMSException {
        run(connection::close);
    }

    @Override
    public ConnectionConsumer createConnectionConsumer(Destination destination,
                                                       String messageSelector,
                                                       ServerSessionPool sessionPool,
                                                       int maxMessages) throws JMSException {

        javax.jms.Destination javaxDestination = ShimUtil.destination(destination);
        javax.jms.ServerSessionPool javaxSessionPool = JavaxJms.create(sessionPool);

        return JakartaJms.create(call(() -> connection.createConnectionConsumer(javaxDestination,
                                                                                messageSelector,
                                                                                javaxSessionPool,
                                                                                maxMessages)));
    }

    @Override
    public ConnectionConsumer createSharedConnectionConsumer(Topic topic,
                                                             String subscriptionName,
                                                             String messageSelector,
                                                             ServerSessionPool sessionPool,
                                                             int maxMessages) throws JMSException {

        javax.jms.ServerSessionPool javaxSessionPool = JavaxJms.create(sessionPool);
        javax.jms.Topic javaxTopic = ShimUtil.topic(topic);

        return JakartaJms.create(call(() -> connection.createSharedConnectionConsumer(javaxTopic,
                                                                                      subscriptionName,
                                                                                      messageSelector,
                                                                                      javaxSessionPool,
                                                                                      maxMessages)));

    }

    @Override
    public ConnectionConsumer createDurableConnectionConsumer(Topic topic,
                                                              String subscriptionName,
                                                              String messageSelector,
                                                              ServerSessionPool sessionPool,
                                                              int maxMessages) throws JMSException {
        javax.jms.ServerSessionPool javaxSessionPool = JavaxJms.create(sessionPool);
        javax.jms.Topic javaxTopic = ShimUtil.topic(topic);

        return JakartaJms.create(call(() -> connection.createDurableConnectionConsumer(javaxTopic,
                                                                                       subscriptionName,
                                                                                       messageSelector,
                                                                                       javaxSessionPool,
                                                                                       maxMessages)));

    }

    @Override
    public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic,
                                                                    String subscriptionName,
                                                                    String messageSelector,
                                                                    ServerSessionPool sessionPool,
                                                                    int maxMessages) throws JMSException {

        javax.jms.ServerSessionPool javaxSessionPool = JavaxJms.create(sessionPool);
        javax.jms.Topic javaxTopic = ShimUtil.topic(topic);

        return JakartaJms.create(call(() -> connection.createSharedDurableConnectionConsumer(javaxTopic,
                                                                                             subscriptionName,
                                                                                             messageSelector,
                                                                                             javaxSessionPool,
                                                                                             maxMessages)));
    }
}
