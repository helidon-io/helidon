/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.mp;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.health.Startup;

@SuppressWarnings("ALL")
class HealthSnippets {

    // tag::snippet_1[]
    @Liveness // <1>
    @ApplicationScoped // <2>
    public class GreetLivenessCheck implements HealthCheck {

        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named("LivenessCheck")  // <3>
                    .up()
                    .withData("time", System.currentTimeMillis())
                    .build();
        }
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    @Readiness // <1>
    @ApplicationScoped
    public class GreetReadinessCheck implements HealthCheck {
        private final AtomicLong readyTime = new AtomicLong(0);

        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named("ReadinessCheck")  // <2>
                    .status(isReady())
                    .withData("time", readyTime.get())
                    .build();
        }

        public void onStartUp(
                @Observes @Initialized(ApplicationScoped.class) Object init) {
            readyTime.set(System.currentTimeMillis()); // <3>
        }

        private boolean isReady() { // <4>
            return Duration.ofMillis(System.currentTimeMillis() - readyTime.get()).getSeconds() >= 5;
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]
    @Startup // <1>
    @ApplicationScoped
    public class GreetStartedCheck implements HealthCheck {
        private final AtomicLong readyTime = new AtomicLong(0);

        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named("StartedCheck")  // <2>
                    .status(isStarted())
                    .withData("time", readyTime.get())
                    .build();
        }

        public void onStartUp(
                @Observes @Initialized(ApplicationScoped.class) Object init) {
            readyTime.set(System.currentTimeMillis()); // <3>
        }

        private boolean isStarted() { // <4>
            return Duration.ofMillis(System.currentTimeMillis() - readyTime.get()).getSeconds() >= 8;
        }
    }
    // end::snippet_3[]

}
