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

package io.helidon.microprofile.messaging;

import java.util.HashMap;
import java.util.Map;

import io.helidon.config.Config;

import org.eclipse.microprofile.reactive.messaging.spi.IncomingConnectorFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Connector as defined in configuration.
 * <br>
 * <pre>{@code
 * mp.messaging.incoming.[channel-name].connector=[connector-name]
 * ...
 * mp.messaging.outgoing.[channel-name].connector=[connector-name]
 * ...
 * mp.messaging.connector.[connector-name].[attribute]=[value]
 * ...
 * }</pre>
 */
class OutgoingConnector implements PublishingConnector {

    private final Config config;
    private final String connectorName;
    private final IncomingConnectorFactory connectorFactory;
    private final Map<String, Publisher<?>> publisherMap = new HashMap<>();

    /**
     * Create new {@link OutgoingConnector}.
     *
     * @param connectorName    {@code [connector-name]} as defined in config
     * @param connectorFactory actual instance of connector bean found in cdi context
     *                         with annotation {@link org.eclipse.microprofile.reactive.messaging.spi.Connector}
     * @param router           {@link io.helidon.microprofile.messaging.ChannelRouter} main orchestrator with root config
     */
    OutgoingConnector(String connectorName, IncomingConnectorFactory connectorFactory, ChannelRouter router) {
        this.connectorName = connectorName;
        this.connectorFactory = connectorFactory;
        this.config = router.getConfig();
    }

    @Override
    public String getConnectorName() {
        return connectorName;
    }

    @Override
    public Config getRootConfig() {
        return config;
    }

    @Override
    public Publisher<?> getPublisher(String channelName) {
        Publisher<?> publisher = publisherMap.computeIfAbsent(channelName, cn -> connectorFactory
                .getPublisherBuilder(getConnectorConfig(channelName))
                .buildRs());
        return publisher;
    }

    @Override
    public void subscribe(String channelName, Subscriber<? super Object> subscriber) {
        getPublisher(channelName).subscribe(subscriber);
    }
}
