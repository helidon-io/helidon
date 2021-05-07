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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import org.eclipse.microprofile.health.HealthCheckResponse;

/**
 * An implementation of HealthCheckResponse, created and returned by HealthCheckResponseProviderImpl.
 */
class HealthCheckResponseImpl extends HealthCheckResponse {
    private final String name;
    private final State state;
    private final TreeMap<String, Object> data;

    HealthCheckResponseImpl(String name, State state, Map<String, Object> data) {
        // Since this constructor is internally called, I'm harsh on accepted values
        Objects.requireNonNull(name);
        Objects.requireNonNull(state);
        Objects.requireNonNull(data);

        // I wrap the "data" map in a TreeMap for two reasons. First, I very much
        // prefer JSON documents to be "stable" in their structure. A HashMap has random
        // ordering of keys, which would lead to random ordering of key/value pairs in
        // the resulting JSON document. Instead, TreeMap will sort by key's natural ordering,
        // so I can have a stable JSON document.
        //
        // Second, I need to return a copy of the original
        // map because a builder can (technically) be reused to stamp out additional instances
        // and previously created instances should not be impacted if the source map was updated
        // subsequent to the previous instances being created!
        this.name = name;
        this.state = state;
        this.data = new TreeMap<>(data);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public Optional<Map<String, Object>> getData() {
        return data.isEmpty() ? Optional.empty() : Optional.of(data);
    }
}
