/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.messaging.connectors.jms;

import java.util.Map;
import java.util.Properties;

import javax.enterprise.inject.Instance;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import io.helidon.config.mp.MpConfig;
import io.helidon.messaging.MessagingException;

import org.eclipse.microprofile.config.Config;

public class ConnectionFactoryLocator {
    private final io.helidon.config.Config helidonConfig;
    private InitialContext ctx;
    private ConnectionFactory connectionFactory;

    static ConnectionFactoryLocator create(Config config, Instance<ConnectionFactory> factoryInstance) {
        return new ConnectionFactoryLocator(config, factoryInstance);
    }

    public ConnectionFactoryLocator(Config config, Instance<ConnectionFactory> factoryInstance) {
        helidonConfig = MpConfig.toHelidonConfig(config);
        if (isJndi()) {
            Properties props = new Properties();
            helidonConfig.detach()
                    .asMap()
                    .orElseGet(Map::of)
                    .forEach((key, val) -> props.setProperty(key.replaceFirst("jndi", "java.naming"), val));
            try {
                ctx = new InitialContext(props);
                connectionFactory = (ConnectionFactory) ctx.lookup("ConnectionFactory");
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
        } else {
            connectionFactory = factoryInstance.stream()
                    //.filter(cf -> config.getValue("connection-factory", Class.class).isAssignableFrom(cf.getClass()))
                    .findFirst()
                    .get();
        }
    }

    boolean isJndi() {
        return helidonConfig.get("jndi").exists();
    }

    ConnectionFactory connectionFactory() {
        return connectionFactory;
    }

    Destination createDestination(Session session) {
        if (isJndi()) {
            try {
                return (Destination) ctx.lookup(helidonConfig.get("destination").asString().get());
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
        } else {
            String type = helidonConfig.get("type").asString().map(String::toLowerCase).get();
            String destination = helidonConfig.get("destination").asString().get();
            try {
                if ("queue".equals(type)) {
                    return session.createQueue(destination);
                } else if ("topic".equals(type)) {
                    return session.createTopic(destination);
                } else {
                    throw new MessagingException("Unknown type");
                }
            } catch (JMSException jmsException) {
                throw new RuntimeException(jmsException);
            }
        }
    }
}
