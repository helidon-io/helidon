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

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.sql.DataSource;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.messaging.MessagingException;
import io.helidon.messaging.connectors.jms.JmsConnector;
import io.helidon.messaging.connectors.jms.JmsMessage;
import io.helidon.messaging.connectors.jms.SessionMetadata;

import oracle.jms.AQjmsConnectionFactory;
import oracle.jms.AQjmsMessage;
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
    public static final String CONNECTOR_NAME = "helidon-aq";

    private static final String DATASOURCE_PROP = "data-source";
    private static final String URL_PROP = "url";
    private static final String USERNAME_PROP = "username";
    private static final String PASSWORD_PROP = "password";

    @Inject
    private Instance<AQjmsConnectionFactory> connectionFactories;

    /**
     * Create new AQConnector.
     *
     * @param config root config for thread context
     */
    @Inject
    AqConnector(final Config config) {
        super(config);
    }

    @Override
    protected Optional<ConnectionFactory> getFactory(io.helidon.config.Config config) {
        // Config always wins
        if (config.get(URL_PROP).exists() || config.get(DATASOURCE_PROP).exists()) {
            try {
                return Optional.of(createAqFactory(config));
            } catch (JMSException e) {
                throw new MessagingException("Error when preparing AQjmsConnectionFactory", e);
            }
        }

        // Check out beans
        return Optional.ofNullable(connectionFactories)
                .flatMap(s -> s.stream().findFirst());
    }

    @Override
    protected Optional<ConnectionFactory> getFactory(String name, io.helidon.config.Config config) {
        // Config always wins
        Config factory = config.get("factory").get(name);
        if (factory.exists()) {
            try {
                return Optional.of(createAqFactory(factory));
            } catch (JMSException e) {
                throw new MessagingException("Error when preparing AQjmsConnectionFactory " + name, e);
            }
        }

        // Check out beans
        return Optional.ofNullable(connectionFactories)
                .flatMap(s -> s.select(NamedLiteral.of(name)).stream().findFirst());
    }

    private AQjmsConnectionFactory createAqFactory(Config c) throws JMSException {
        ConfigValue<String> user = c.get(USERNAME_PROP).asString();
        ConfigValue<String> password = c.get(PASSWORD_PROP).asString();
        ConfigValue<String> url = c.get(URL_PROP).asString();
        ConfigValue<String> dataSourceName = c.get(DATASOURCE_PROP).asString();
        AQjmsConnectionFactory fact = new AQjmsConnectionFactory();
        if (dataSourceName.isPresent()) {
            if (user.isPresent()) {
                throw new MessagingException("When " + DATASOURCE_PROP + " is set, properties "
                        + String.join(", ", USERNAME_PROP, PASSWORD_PROP, URL_PROP)
                        + " are forbidden!");
            }

            Instance<DataSource> dataSources = CDI.current().select(DataSource.class, NamedLiteral.of(dataSourceName.get()));
            if (dataSources.isResolvable()) {
                fact.setDatasource(dataSources.get());
            } else {
                throw new MessagingException("Datasource " + dataSourceName.get()
                        + (dataSources.isAmbiguous() ? " is ambiguous!" : " not found!"));
            }
        }
        if (url.isPresent()) {
            fact.setJdbcURL(url.get());
        }
        if (user.isPresent()) {
            fact.setUsername(user.get());
        }
        if (password.isPresent()) {
            fact.setPassword(password.get());
        }
        return fact;
    }


    @Override
    protected JmsMessage<?> createMessage(final javax.jms.Message message,
                                          Executor executor,
                                          final SessionMetadata sessionMetadata) {
        return AqMessage.of((AQjmsMessage) message, executor, sessionMetadata);
    }

    @Override
    protected BiConsumer<Message<?>, JMSException> sendingErrorHandler(final Config config) {
        return (m, e) -> {
            throw new MessagingException("Error during sending Oracle AQ JMS message.", e);
        };
    }

    @Override
    public void stop() {
        super.stop();
    }
}
