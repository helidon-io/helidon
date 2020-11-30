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
 */

package io.helidon.messaging.connectors.jms;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import javax.jms.ConnectionFactory;

import io.helidon.common.Builder;
import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.config.Config;

/**
 * Builder for {@link io.helidon.messaging.connectors.jms.JmsConnector}.
 */
public class JmsConnectorBuilder implements Builder<JmsConnector> {

    private final Map<String, ConnectionFactory> connectionFactoryMap = new HashMap<>();
    private ScheduledExecutorService scheduler;
    private ExecutorService executor;
    private Config config;

    /**
     * Add custom {@link javax.jms.ConnectionFactory ConnectionFactory} referencable by supplied name with
     * {@link JmsConnector#NAMED_FACTORY_ATTRIBUTE}.
     *
     * @param name              referencable connection factory name
     * @param connectionFactory custom connection factory
     * @return this builder
     */
    public JmsConnectorBuilder connectionFactory(String name, ConnectionFactory connectionFactory) {
        connectionFactoryMap.put(name, connectionFactory);
        return this;
    }

    /**
     * Custom configuration for connector.
     *
     * @param config custom config
     * @return this builder
     */
    public JmsConnectorBuilder config(Config config) {
        this.config = config;
        return this;
    }

    /**
     * Custom executor for asynchronous operations like acknowledgement.
     *
     * @param executor custom executor service
     * @return this builder
     */
    public JmsConnectorBuilder executor(ExecutorService executor) {
        this.executor = executor;
        return this;
    }

    /**
     * Custom executor for loop pulling messages from JMS.
     *
     * @param scheduler custom scheduled executor service
     * @return this builder
     */
    public JmsConnectorBuilder scheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
        return this;
    }

    @Override
    public JmsConnector build() {
        if (config == null) {
            config = Config.create();
        }

        if (executor == null) {
            executor = ThreadPoolSupplier.builder()
                    .threadNamePrefix(JmsConnector.EXECUTOR_THREAD_NAME_PREFIX)
                    .config(config)
                    .build()
                    .get();
        }
        if (scheduler == null) {
            scheduler = ScheduledThreadPoolSupplier.builder()
                    .threadNamePrefix(JmsConnector.SCHEDULER_THREAD_NAME_PREFIX)
                    .config(config)
                    .build()
                    .get();
        }

        return new JmsConnector(connectionFactoryMap, scheduler, executor);
    }

}
