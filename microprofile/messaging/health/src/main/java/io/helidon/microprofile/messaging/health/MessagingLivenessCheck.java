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

package io.helidon.microprofile.messaging.health;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.helidon.microprofile.messaging.MessagingCdiExtension;

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
public class MessagingLivenessCheck implements HealthCheck {

    private final MessagingCdiExtension messagingCdiExtension;

    @Inject
    MessagingLivenessCheck(MessagingCdiExtension messagingCdiExtension) {
        this.messagingCdiExtension = messagingCdiExtension;
    }

    @Override
    public HealthCheckResponse call() {
        Map<String, Boolean> channelsHealth = messagingCdiExtension.channelsLiveness();
        HealthCheckResponseBuilder b = HealthCheckResponse.builder()
                .name("messaging");
        AtomicBoolean isUp = new AtomicBoolean(true);
        channelsHealth.forEach((channelName, up) -> {
            isUp.compareAndSet(true, up);
            b.withData(channelName, up ? "UP" : "DOWN");
        });
        b.state(isUp.get());
        return b.build();
    }
}
