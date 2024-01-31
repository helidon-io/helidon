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
package io.helidon.tracing.providers.opentelemetry;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.tracing.Baggage;

class OpenTelemetryBaggage implements Baggage {

    private final io.opentelemetry.api.baggage.Baggage delegate;

    OpenTelemetryBaggage(io.opentelemetry.api.baggage.Baggage delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<String> get(String key) {
        Objects.requireNonNull(key, "baggage key cannot be null");
        return Optional.ofNullable(delegate.getEntryValue(key));
    }

    @Override
    public Set<String> keys() {
        return Collections.unmodifiableSet(delegate.asMap().keySet());
    }

    @Override
    public boolean containsKey(String key) {
        Objects.requireNonNull(key, "baggage key cannot be null");
        return delegate.getEntryValue(key) != null;
    }
}
