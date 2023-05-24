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
import org.eclipse.microprofile.health.Liveness;

/**
 * MicroProfile Reactive Messaging liveness check.
 * If any of the channels go down, messaging is considered to be down.
 */
@Liveness
@ApplicationScoped
@BuiltInHealthCheck
public class MessagingLivenessCheck implements HealthCheck, MessagingChannelProcessor {

    private final Map<String, Boolean> liveChannels = new ConcurrentHashMap<>();

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder b = HealthCheckResponse.builder()
                .name("messaging");
        AtomicBoolean isUp = new AtomicBoolean(true);
        liveChannels.forEach((channelName, up) -> {
            isUp.compareAndSet(true, up);
            b.withData(channelName, up ? "UP" : "DOWN");
        });
        b.status(isUp.get());
        return b.build();
    }

    @Override
    public void onError(String channelName, Throwable t) {
        liveChannels.put(channelName, false);
    }

    @Override
    public void onCancel(String channelName) {
        liveChannels.put(channelName, false);
    }

    @Override
    public void onComplete(String channelName) {
        liveChannels.put(channelName, false);
    }

    @Override
    public void onInit(String channelName) {
        liveChannels.put(channelName, true);
    }
}
