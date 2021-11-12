/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.jms;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;

/**
 * Metadata of the JMS session.
 */
public class SessionMetadata {
    private final Session session;
    private final Connection connection;
    private final ConnectionFactory connectionFactory;

    SessionMetadata(Session session, Connection connection, ConnectionFactory connectionFactory) {
        this.session = session;
        this.connection = connection;
        this.connectionFactory = connectionFactory;
    }

    /**
     * {@link jakarta.jms.Session} used for receiving the message.
     *
     * @return JMS session
     */
    public Session session() {
        return session;
    }

    /**
     * {@link jakarta.jms.Connection} used for receiving the message.
     *
     * @return JMS connection
     */
    public Connection connection() {
        return connection;
    }

    /**
     * {@link jakarta.jms.ConnectionFactory} used for receiving the message.
     *
     * @return JMS connection factory
     */
    public ConnectionFactory connectionFactory() {
        return connectionFactory;
    }
}
