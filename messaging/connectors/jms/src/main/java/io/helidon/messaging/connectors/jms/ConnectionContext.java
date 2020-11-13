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
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import io.helidon.config.Config;
import io.helidon.messaging.MessagingException;

import static io.helidon.messaging.connectors.jms.JmsConnector.JNDI_ATTRIBUTE;
import static io.helidon.messaging.connectors.jms.JmsConnector.JNDI_DESTINATION_ATTRIBUTE;
import static io.helidon.messaging.connectors.jms.JmsConnector.JNDI_JMS_FACTORY_ATTRIBUTE;
import static io.helidon.messaging.connectors.jms.JmsConnector.JNDI_JMS_FACTORY_DEFAULT;
import static io.helidon.messaging.connectors.jms.JmsConnector.JNDI_PROPS_ATTRIBUTE;

/**
 * Context related to one actual connection being constructed by JMS connector.
 */
public class ConnectionContext {

    private static final Logger LOGGER = Logger.getLogger(ConnectionContext.class.getName());

    private final Config config;
    private final InitialContext ctx;

    ConnectionContext(io.helidon.config.Config config) {
        this.config = config;
        if (isJndi()) {
            Properties props = new Properties();
            config.get(JNDI_ATTRIBUTE)
                    .get(JNDI_PROPS_ATTRIBUTE)
                    .detach()
                    .asMap()
                    .orElseGet(Map::of)
                    .forEach(props::setProperty);
            try {
                ctx = new InitialContext(props);
            } catch (NamingException e) {
                throw new MessagingException("Error when preparing JNDI context.", e);
            }
        } else {
            ctx = null;
        }
    }

    boolean isJndi() {
        return config.get(JNDI_ATTRIBUTE).exists();
    }

    Optional<? extends ConnectionFactory> lookupFactory() {
        return lookupFactory(config.get(JNDI_ATTRIBUTE)
                .get(JNDI_JMS_FACTORY_ATTRIBUTE)
                .asString()
                .orElse(JNDI_JMS_FACTORY_DEFAULT));
    }

    Optional<? extends Destination> lookupDestination() {
        return config.get(JNDI_ATTRIBUTE)
                .get(JNDI_DESTINATION_ATTRIBUTE)
                .asString()
                .map(this::lookupDestination)
                .orElseGet(Optional::empty);
    }

    Optional<? extends ConnectionFactory> lookupFactory(String jndi) {
        return Optional.ofNullable((ConnectionFactory) lookup(jndi));
    }

    Optional<? extends Destination> lookupDestination(String jndi) {
        return Optional.ofNullable((Destination) lookup(jndi));
    }


    /**
     * Channel config.
     *
     * @return config
     */
    public Config config() {
        return config;
    }

    private Object lookup(String jndi) {
        try {
            return ctx.lookup(jndi);
        } catch (NamingException e) {
            LOGGER.log(Level.FINE, e, () -> "JNDI lookup of " + jndi + " failed");
            return null;
        }
    }
}
