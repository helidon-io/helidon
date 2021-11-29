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
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;

/**
 * Exposes Jakarta API, delegates to javax API.
 */
class JakartaConnectionFactory implements ConnectionFactory {
    private final javax.jms.ConnectionFactory connectionFactory;

    JakartaConnectionFactory(javax.jms.ConnectionFactory connectionFactoryFactory) {
        this.connectionFactory = connectionFactoryFactory;
    }

    @Override
    public Connection createConnection() throws JMSException {
        try {
            return JakartaJms.create(connectionFactory.createConnection());
        } catch (javax.jms.JMSException e) {
            JMSException jmsE = new JMSException(e.getMessage(), e.getErrorCode());
            jmsE.addSuppressed(e);
            throw jmsE;
        }
    }

    @Override
    public Connection createConnection(String username, String password) throws JMSException {
        try {
            return JakartaJms.create(connectionFactory.createConnection(username, password));
        } catch (javax.jms.JMSException e) {
            JMSException jmsE = new JMSException(e.getMessage(), e.getErrorCode());
            jmsE.addSuppressed(e);
            throw jmsE;
        }
    }

    @Override
    public JMSContext createContext() {
        return JakartaJms.create(connectionFactory.createContext());
    }

    @Override
    public JMSContext createContext(String username, String password) {
        return JakartaJms.create(connectionFactory.createContext(username, password));
    }

    @Override
    public JMSContext createContext(String username, String password, int sessionMode) {
        return JakartaJms.create(connectionFactory.createContext(username, password, sessionMode));
    }

    @Override
    public JMSContext createContext(int sessionMode) {
        return JakartaJms.create(connectionFactory.createContext(sessionMode));
    }
}
