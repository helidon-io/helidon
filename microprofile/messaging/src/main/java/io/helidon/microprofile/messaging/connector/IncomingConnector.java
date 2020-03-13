/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.messaging.connector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.config.Config;
import io.helidon.microprofile.messaging.channel.ChannelRouter;

import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
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
public class IncomingConnector implements SubscribingConnector {

    private final Config config;
    private final String connectorName;
    private final OutgoingConnectorFactory connectorFactory;
    private final Map<String, Subscriber> subscriberMap = new ConcurrentHashMap<>();

    /**
     * Create new {@link IncomingConnector}.
     *
     * @param connectorName    {@code [connector-name]} as defined in config
     * @param connectorFactory actual instance of connector bean found in cdi context
     *                         with annotation {@link org.eclipse.microprofile.reactive.messaging.spi.Connector}
     * @param router           {@link io.helidon.microprofile.messaging.channel.ChannelRouter} main orchestrator with root config
     */
    public IncomingConnector(String connectorName, OutgoingConnectorFactory connectorFactory, ChannelRouter router) {
        this.connectorName = connectorName;
        this.connectorFactory = connectorFactory;
        this.config = router.getConfig();
    }

    @Override
    public Subscriber getSubscriber(String channelName) {
        Subscriber subscriber = subscriberMap.computeIfAbsent(channelName, cn -> connectorFactory
                .getSubscriberBuilder(getConnectorConfig(cn)).build());
        return subscriber;
    }

    @Override
    public String getConnectorName() {
        return connectorName;
    }

    @Override
    public Config getRootConfig() {
        return config;
    }


}
