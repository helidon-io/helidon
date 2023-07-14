/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.messaging.metrics;

import io.helidon.microprofile.messaging.MessagingChannelProcessor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Messaging counter for reactive messaging channels.
 */
@ApplicationScoped
public class MessagingCounter implements MessagingChannelProcessor {

    private final MetricRegistry metricsRegistry;

    // TODO change to RegistryScope once MP makes it a qualifier
    @Inject
    MessagingCounter(@RegistryType(type = MetricRegistry.Type.BASE) MetricRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    public Message<?> map(String channelName, Message<?> message) {
        metricsRegistry.counter("mp.messaging.message.count", new Tag("channel", channelName)).inc();
        return message;
    }
}
