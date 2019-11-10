/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.config.Config;
import io.helidon.microprofile.messaging.channel.ChannelRouter;
import org.eclipse.microprofile.reactive.messaging.spi.OutgoingConnectorFactory;
import org.reactivestreams.Subscriber;

import java.util.HashMap;
import java.util.Map;

public class IncomingConnector implements SubscribingConnector {

    private final Config config;
    private String connectorName;
    private OutgoingConnectorFactory connectorFactory;
    private ChannelRouter router;
    private Map<String, Subscriber> subscriberMap = new HashMap<>();

    public IncomingConnector(String connectorName, OutgoingConnectorFactory connectorFactory, ChannelRouter router) {
        this.connectorName = connectorName;
        this.connectorFactory = connectorFactory;
        this.router = router;
        this.config = router.getConfig();
    }

    @Override
    public Subscriber getSubscriber(String channelName) {
        Subscriber subscriber = subscriberMap.get(channelName);
        if (subscriber == null) {
            subscriber = connectorFactory.getSubscriberBuilder(getConnectorConfig(channelName)).build();
            subscriberMap.put(channelName, subscriber);
        }
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
