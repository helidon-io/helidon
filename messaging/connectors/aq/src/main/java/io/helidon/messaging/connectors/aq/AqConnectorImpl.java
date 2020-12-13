/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.sql.DataSource;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.messaging.MessagingException;
import io.helidon.messaging.connectors.jms.ConnectionContext;
import io.helidon.messaging.connectors.jms.JmsConnector;
import io.helidon.messaging.connectors.jms.JmsMessage;
import io.helidon.messaging.connectors.jms.SessionMetadata;

import oracle.jms.AQjmsConnectionFactory;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;

/**
 * MicroProfile Reactive Messaging Oracle AQ connector.
 */
@ApplicationScoped
@Connector(AqConnector.CONNECTOR_NAME)
public class AqConnectorImpl extends JmsConnector implements AqConnector {

    private static final LazyValue<Boolean> IS_CDI = LazyValue.create(() -> {
        try {
            Class.forName("javax.enterprise.inject.spi.CDI", false, AqConnectorImpl.class.getClassLoader());
            CDI.current();
            return true;
        } catch (ClassNotFoundException | IllegalStateException e) {
            return false;
        }
    });

    private final Instance<AQjmsConnectionFactory> connectionFactories;
    private final Map<String, DataSource> dataSourceMap;

    /**
     * Create new AQConnector.
     *
     * @param config root config for thread context
     */
    @Inject
    AqConnectorImpl(Config config, Instance<AQjmsConnectionFactory> connectionFactories) {
        super(config, null);
        this.connectionFactories = connectionFactories;
        this.dataSourceMap = Map.of();
    }

    AqConnectorImpl(Map<String, DataSource> dataSourceMap, ScheduledExecutorService scheduler, ExecutorService executor) {
        super(Map.of(), scheduler, executor);
        this.connectionFactories = null;
        this.dataSourceMap = dataSourceMap;
    }

    @Override
    protected Optional<? extends ConnectionFactory> getFactory(ConnectionContext ctx) {

        // Named factory
        ConfigValue<String> factoryName = ctx.config().get(NAMED_FACTORY_ATTRIBUTE).asString();
        if (factoryName.isPresent()) {
            Config factory = ctx.config().get("factory").get(factoryName.get());
            if (factory.exists()) {
                // from config
                try {
                    return Optional.of(createAqFactory(factory));
                } catch (JMSException e) {
                    throw new MessagingException("Error when preparing AQjmsConnectionFactory " + factoryName.get(), e);
                }
            } else {
                // or named bean
                return Optional.ofNullable(connectionFactories)
                        .flatMap(s -> s.select(NamedLiteral.of(factoryName.get())).stream().findFirst());
            }
        }

        // per channel config
        if (ctx.config().get(URL_ATTRIBUTE).exists() || ctx.config().get(DATASOURCE_ATTRIBUTE).exists()) {
            try {
                return Optional.of(createAqFactory(ctx.config()));
            } catch (JMSException e) {
                throw new MessagingException("Error when preparing AQjmsConnectionFactory", e);
            }
        }

        // Check out not named beans
        return Optional.ofNullable(connectionFactories)
                .flatMap(s -> s.stream()
                        .findFirst()
                );
    }

    private AQjmsConnectionFactory createAqFactory(Config c) throws JMSException {
        ConfigValue<String> user = c.get(USERNAME_ATTRIBUTE).asString();
        ConfigValue<String> password = c.get(PASSWORD_ATTRIBUTE).asString();
        ConfigValue<String> url = c.get(URL_ATTRIBUTE).asString();
        ConfigValue<String> dataSourceName = c.get(DATASOURCE_ATTRIBUTE).asString();
        AQjmsConnectionFactory fact = new AQjmsConnectionFactory();
        if (dataSourceName.isPresent()) {
            if (user.isPresent()) {
                throw new MessagingException("When " + DATASOURCE_ATTRIBUTE + " is set, properties "
                        + String.join(", ", USERNAME_ATTRIBUTE, PASSWORD_ATTRIBUTE, URL_ATTRIBUTE)
                        + " are forbidden!");
            }

            // DataSource provided via SE builder
            DataSource seDataSource = dataSourceMap.get(dataSourceName.get());
            if (seDataSource != null) {
                fact.setDatasource(seDataSource);
            }

            // DataSource provided via CDI as named bean
            if (IS_CDI.get()) {
                Instance<DataSource> dataSources = CDI.current().select(DataSource.class, NamedLiteral.of(dataSourceName.get()));
                if (dataSources.isResolvable()) {
                    fact.setDatasource(dataSources.get());
                } else {
                    throw new MessagingException("Datasource " + dataSourceName.get()
                            + (dataSources.isAmbiguous() ? " is ambiguous!" : " not found!"));
                }
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
    protected JmsMessage<?> createMessage(javax.jms.Message message,
                                          Executor executor,
                                          SessionMetadata sessionMetadata) {
        return new AqMessageImpl<>(super.createMessage(message, executor, sessionMetadata), sessionMetadata);
    }

    @Override
    protected BiConsumer<Message<?>, JMSException> sendingErrorHandler(Config config) {
        return (m, e) -> {
            throw new MessagingException("Error during sending Oracle AQ JMS message.", e);
        };
    }

    @Override
    public void stop() {
        super.stop();
    }
}
