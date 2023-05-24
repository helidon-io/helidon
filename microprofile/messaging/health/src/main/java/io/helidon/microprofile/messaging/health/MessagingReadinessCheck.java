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

package io.helidon.microprofile.messaging.health;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.health.common.BuiltInHealthCheck;
import io.helidon.microprofile.messaging.MessagingChannelProcessor;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * MicroProfile Reactive Messaging readiness check.
 * Until all channels is up, messaging is considered to be down.
 */
@Readiness
@ApplicationScoped
@BuiltInHealthCheck
public class MessagingReadinessCheck implements HealthCheck, MessagingChannelProcessor {

    private final Map<String, Boolean> readyChannels = new ConcurrentHashMap<>();

    @Override
    public HealthCheckResponse call() {
        AtomicBoolean isUp = new AtomicBoolean(true);
        HealthCheckResponseBuilder b = HealthCheckResponse.builder()
                .name("messaging");
        readyChannels.forEach((channelName, up) -> {
            isUp.compareAndSet(true, up);
            b.withData(channelName, up ? "UP" : "DOWN");
        });
        b.status(isUp.get());
        return b.build();
    }

    @Override
    public void onSubscribe(String channelName, Subscriber<Message<?>> subscriber, Subscription subscription) {
        readyChannels.put(channelName, true);
    }

    @Override
    public void onInit(String channelName) {
        readyChannels.put(channelName, false);
    }
}
