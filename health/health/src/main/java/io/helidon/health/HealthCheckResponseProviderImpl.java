/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.health;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.spi.HealthCheckResponseProvider;

/**
 * An implementation of HealthCheckResponseProvider which does not rely on any particular java-to-json mapping strategy.
 */
public class HealthCheckResponseProviderImpl implements HealthCheckResponseProvider {
    @Override
    public HealthCheckResponseBuilder createResponseBuilder() {
        return new HealthCheckResponseBuilder() {
            private final Map<String, Object> data = new HashMap<>();
            private String name;
            private HealthCheckResponse.State state = HealthCheckResponse.State.UP;

            @Override
            public HealthCheckResponseBuilder name(String name) {
                // NOTE: The spec doesn't say what to do with a null name, so I just disallow it
                Objects.requireNonNull(name, "Name cannot be null");
                this.name = name;
                return this;
            }

            @Override
            public HealthCheckResponseBuilder withData(String key, String value) {
                // NOTE: The spec doesn't say what to do with a null key, so I just disallow it
                Objects.requireNonNull(key, "key cannot be null");

                this.data.put(key, value);
                return this;
            }

            @Override
            public HealthCheckResponseBuilder withData(String key, long value) {
                // NOTE: The spec doesn't say what to do with a null key, so I just disallow it
                Objects.requireNonNull(key, "key cannot be null");

                this.data.put(key, value);
                return this;
            }

            @Override
            public HealthCheckResponseBuilder withData(String key, boolean value) {
                // NOTE: The spec doesn't say what to do with a null key, so I just disallow it
                Objects.requireNonNull(key, "key cannot be null");

                this.data.put(key, value);
                return this;
            }

            @Override
            public HealthCheckResponseBuilder up() {
                this.state = HealthCheckResponse.State.UP;
                return this;
            }

            @Override
            public HealthCheckResponseBuilder down() {
                this.state = HealthCheckResponse.State.DOWN;
                return this;
            }

            @Override
            public HealthCheckResponseBuilder state(boolean up) {
                if (up) {
                    up();
                } else {
                    down();
                }

                return this;
            }

            @Override
            public HealthCheckResponse build() {
                return new HealthCheckResponseImpl(name, state, data);
            }
        };
    }
}
