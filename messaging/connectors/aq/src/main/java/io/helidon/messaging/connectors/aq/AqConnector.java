/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.aq;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import javax.sql.DataSource;

import io.helidon.common.Builder;
import io.helidon.common.config.Config;
import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.messaging.connectors.jms.JmsConnector;

import org.eclipse.microprofile.reactive.messaging.spi.ConnectorAttribute;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;

/**
 * Reactive Messaging Oracle AQ connector.
 */
@ConnectorAttribute(name = AqConnector.DATASOURCE_ATTRIBUTE,
        description = "name of the datasource bean used to connect Oracle DB with AQ",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = AqConnector.URL_ATTRIBUTE,
        description = "jdbc connection string used to connect Oracle DB with AQ (forbidden when datasource is specified)",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.USERNAME_ATTRIBUTE,
        description = "User name used to connect JMS session",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.PASSWORD_ATTRIBUTE,
        description = "Password to connect JMS session",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.TYPE_ATTRIBUTE,
        description = "Possible values are: queue, topic",
        defaultValue = "queue",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.DESTINATION_ATTRIBUTE,
        description = "Queue or topic name",
        mandatory = true,
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.ACK_MODE_ATTRIBUTE,
        description = "Possible values are: "
                + "AUTO_ACKNOWLEDGE- session automatically acknowledges a client’s receipt of a message, "
                + "CLIENT_ACKNOWLEDGE - receipt of a message is acknowledged only when Message.ack() is called manually, "
                + "DUPS_OK_ACKNOWLEDGE - session lazily acknowledges the delivery of messages.",
        defaultValue = "AUTO_ACKNOWLEDGE",
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "io.helidon.messaging.connectors.jms.AcknowledgeMode")
@ConnectorAttribute(name = JmsConnector.TRANSACTED_ATTRIBUTE,
        description = "Indicates whether the session will use a local transaction.",
        mandatory = false,
        defaultValue = "false",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "boolean")
@ConnectorAttribute(name = JmsConnector.AWAIT_ACK_ATTRIBUTE,
        description = "Wait for the acknowledgement of previous message before pulling next one.",
        mandatory = false,
        defaultValue = "false",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "boolean")
@ConnectorAttribute(name = JmsConnector.MESSAGE_SELECTOR_ATTRIBUTE,
        description = "JMS API message selector expression based on a subset of the SQL92. "
                + "Expression can only access headers and properties, not the payload.",
        mandatory = false,
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.CLIENT_ID_ATTRIBUTE,
        description = "Client identifier for JMS connection.",
        mandatory = false,
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.DURABLE_ATTRIBUTE,
        description = "True for creating durable consumer (only for topic).",
        mandatory = false,
        defaultValue = "false",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "boolean")
@ConnectorAttribute(name = JmsConnector.SUBSCRIBER_NAME_ATTRIBUTE,
        description = "Subscriber name for durable consumer used to identify subscription.",
        mandatory = false,
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.NON_LOCAL_ATTRIBUTE,
        description = "If true then any messages published to the topic using this session’s connection, "
                + "or any other connection with the same client identifier, "
                + "will not be added to the durable subscription.",
        mandatory = false,
        defaultValue = "false",
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "boolean")
@ConnectorAttribute(name = JmsConnector.NAMED_FACTORY_ATTRIBUTE,
        description = "Select in case factory is injected as a named bean or configured with name.",
        mandatory = false,
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
@ConnectorAttribute(name = JmsConnector.POLL_TIMEOUT_ATTRIBUTE,
        description = "Timeout for polling for next message in every poll cycle in millis. Default value: 50",
        mandatory = false,
        defaultValue = "50",
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "long")
@ConnectorAttribute(name = JmsConnector.PERIOD_EXECUTIONS_ATTRIBUTE,
        description = "Period for executing poll cycles in millis.",
        mandatory = false,
        defaultValue = "100",
        direction = ConnectorAttribute.Direction.INCOMING,
        type = "long")
@ConnectorAttribute(name = JmsConnector.SESSION_GROUP_ID_ATTRIBUTE,
        description = "When multiple channels share same session-group-id, "
                + "they share same JMS session and same JDBC connection as well.",
        mandatory = false,
        direction = ConnectorAttribute.Direction.INCOMING_AND_OUTGOING,
        type = "string")
public interface AqConnector extends ConnectorFactory {

    /**
     * Oracle AQ connector name.
     */
    String CONNECTOR_NAME = "helidon-aq";

    /**
     * Configuration key for data source identifier.
     */
    String DATASOURCE_ATTRIBUTE = "data-source";

    /**
     * Configuration key for Oracle db connection string.
     */
    String URL_ATTRIBUTE = "url";

    /**
     * Configuration key for thread name prefix used for asynchronous operations like acknowledgement.
     */
    String EXECUTOR_THREAD_NAME_PREFIX = "aq-";

    /**
     * Configuration key for thread name prefix used for polling.
     */
    String SCHEDULER_THREAD_NAME_PREFIX = "aq-poll-";

    /**
     * Provides a {@link io.helidon.messaging.connectors.jms.JmsConnector.JmsConnectorBuilder} for creating
     * a {@link io.helidon.messaging.connectors.jms.JmsConnector} instance.
     *
     * @return new Builder instance
     */
    static AqConnectorBuilder builder() {
        return new AqConnectorBuilder();
    }

    /**
     * Custom config builder for AQ connector.
     *
     * @return new AQ specific config builder
     */
    static AqConfigBuilder configBuilder() {
        return new AqConfigBuilder();
    }

    /**
     * Builder for {@link AqConnectorImpl}.
     */
    class AqConnectorBuilder implements Builder<AqConnectorBuilder, AqConnectorImpl> {

        private final Map<String, DataSource> dataSourceMap = new HashMap<>();
        private ScheduledExecutorService scheduler;
        private ExecutorService executor;
        private Config config;

        /**
         * Add custom {@link jakarta.jms.ConnectionFactory ConnectionFactory} referencable by supplied name with
         * {@value io.helidon.messaging.connectors.jms.JmsConnector#NAMED_FACTORY_ATTRIBUTE}.
         *
         * @param name       referencable connection factory name
         * @param dataSource custom connection factory
         * @return this builder
         */
        public AqConnectorBuilder dataSource(String name, DataSource dataSource) {
            dataSourceMap.put(name, dataSource);
            return this;
        }

        /**
         * Custom configuration for connector.
         *
         * @param config custom config
         * @return this builder
         */
        public AqConnectorBuilder config(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Custom executor for asynchronous operations like acknowledgement.
         *
         * @param executor custom executor service
         * @return this builder
         */
        public AqConnectorBuilder executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Custom executor for loop pulling messages from JMS.
         *
         * @param scheduler custom scheduled executor service
         * @return this builder
         */
        public AqConnectorBuilder scheduler(ScheduledExecutorService scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        /**
         * Custom executor supplier for asynchronous operations like acknowledgement.
         *
         * @param executorSupplier custom executor service
         * @return this builder
         */
        public AqConnectorBuilder executor(ThreadPoolSupplier executorSupplier) {
            this.executor = executorSupplier.get();
            return this;
        }

        /**
         * Custom executor supplier for loop pulling messages from JMS.
         *
         * @param schedulerPoolSupplier custom scheduled executor service
         * @return this builder
         */
        public AqConnectorBuilder scheduler(ScheduledThreadPoolSupplier schedulerPoolSupplier) {
            this.scheduler = schedulerPoolSupplier.get();
            return this;
        }

        @Override
        public AqConnectorImpl build() {
            if (config == null) {
                config = Config.empty();
            }

            if (executor == null) {
                executor = ThreadPoolSupplier.builder()
                        .threadNamePrefix(AqConnector.EXECUTOR_THREAD_NAME_PREFIX)
                        .config(config)
                        .build()
                        .get();
            }
            if (scheduler == null) {
                scheduler = ScheduledThreadPoolSupplier.builder()
                        .threadNamePrefix(AqConnector.SCHEDULER_THREAD_NAME_PREFIX)
                        .config(config)
                        .build()
                        .get();
            }

            return new AqConnectorImpl(dataSourceMap, scheduler, executor);
        }
    }
}
