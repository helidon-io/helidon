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
 */

package io.helidon.messaging.connectors.aq;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import javax.sql.DataSource;

import io.helidon.common.Builder;
import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.common.configurable.ThreadPoolSupplier;

import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;

/**
 * Reactive Messaging Oracle AQ connector.
 */
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
    class AqConnectorBuilder implements Builder<AqConnectorImpl> {

        private final Map<String, DataSource> dataSourceMap = new HashMap<>();
        private ScheduledExecutorService scheduler;
        private ExecutorService executor;
        private io.helidon.config.Config config;

        /**
         * Add custom {@link javax.jms.ConnectionFactory ConnectionFactory} referencable by supplied name with
         * {@link io.helidon.messaging.connectors.jms.JmsConnector#NAMED_FACTORY_ATTRIBUTE}.
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
        public AqConnectorBuilder config(io.helidon.config.Config config) {
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
                config = io.helidon.config.Config.create();
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
