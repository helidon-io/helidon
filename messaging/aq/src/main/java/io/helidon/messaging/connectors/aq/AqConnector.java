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

package io.helidon.messaging.connectors.aq;

import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.jms.ConnectionFactory;

import io.helidon.config.Config;
import io.helidon.messaging.connectors.jms.JmsConnector;
import io.helidon.messaging.connectors.jms.SessionMetadata;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;

/**
 * MicroProfile Reactive Messaging Oracle AQ connector.
 */
@ApplicationScoped
@Connector(AqConnector.CONNECTOR_NAME)
public class AqConnector extends JmsConnector {

    private static final Logger LOGGER = Logger.getLogger(AqConnector.class.getName());
    /**
     * Microprofile messaging Oracle AQ connector name.
     */
    static final String CONNECTOR_NAME = "helidon-aq";

    /**
     * Create new JmsConnector.
     *
     * @param connectionFactories pre-prepared connection factories
     * @param config              root config for thread context
     */
    public AqConnector(final Instance<ConnectionFactory> connectionFactories, final Config config) {
        super(connectionFactories, config);
    }

    @Override
    protected Message<?> createMessage(final javax.jms.Message message, final SessionMetadata sessionMetadata) {
        return super.createMessage(message, sessionMetadata);
    }

    @Override
    public void stop() {
        super.stop();
    }
}
