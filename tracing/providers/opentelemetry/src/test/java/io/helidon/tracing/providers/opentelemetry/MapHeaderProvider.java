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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.tracing.HeaderProvider;

record MapHeaderProvider(Map<String, List<String>> values) implements HeaderProvider {

    @Override
    public Iterable<String> keys() {
        return values.keySet();
    }

    @Override
    public Optional<String> get(String key) {
        if (!values.containsKey(key)) {
            return Optional.empty();
        }
        List<String> matches = values.get(key);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    @Override
    public Iterable<String> getAll(String key) {
        return values.get(key);
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }
}
