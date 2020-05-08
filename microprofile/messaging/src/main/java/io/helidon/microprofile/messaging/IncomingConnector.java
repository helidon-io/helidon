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
class IncomingConnector implements SubscribingConnector {

    private final Config config;
    private final String connectorName;
    private final OutgoingConnectorFactory connectorFactory;
    private final Map<String, Subscriber<? super Object>> subscriberMap = new HashMap<>();

    /**
     * Create new {@link IncomingConnector}.
     *
     * @param connectorName    {@code [connector-name]} as defined in config
     * @param connectorFactory actual instance of connector bean found in cdi context
     *                         with annotation {@link org.eclipse.microprofile.reactive.messaging.spi.Connector}
     * @param router           {@link io.helidon.microprofile.messaging.ChannelRouter} main orchestrator with root config
     */
    IncomingConnector(String connectorName, OutgoingConnectorFactory connectorFactory, ChannelRouter router) {
        this.connectorName = connectorName;
        this.connectorFactory = connectorFactory;
        this.config = router.getConfig();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Subscriber<? super Object> getSubscriber(String channelName) {
        Subscriber<? super Object> subscriber = subscriberMap.computeIfAbsent(
                channelName, cn -> (Subscriber<? super Object>) (Subscriber<?>)
                connectorFactory.getSubscriberBuilder(getConnectorConfig(channelName)).build());
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
