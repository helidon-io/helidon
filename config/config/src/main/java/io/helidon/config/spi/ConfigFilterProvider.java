/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.config.spi;

import io.helidon.config.Config;
import io.helidon.service.registry.Service;

/**
 * Java service loader service to create config filter factories.
 *
 * @see io.helidon.config.Config.Builder#addFilterProvider(ConfigFilterProvider)
 */
@FunctionalInterface
@Service.Contract
public interface ConfigFilterProvider {
    /**
     * Creates a factory once for each invocation of {@link Config.Builder#build()}.
     * This method may be called concurrently.
     * <p>
     * The supplied configuration contains merged source values before value filtering.
     * {@link Config#get(String)} and {@link Config#detach()} return nodes in this unfiltered view.
     * {@link Config.Context#last()} and {@link Config.Context#reload()} return the same unfiltered node;
     * {@link Config.Context#stopChangeSupport()} and {@link Config#onChange(java.util.function.Consumer)} have no effect.
     * <p>
     * Values from lazy sources are read on demand. Capture the values needed by a filter during its creation so later
     * source changes cannot affect that filter.
     *
     * @param initialConfig unfiltered initial source configuration
     * @return new factory
     */
    ConfigFilterFactory create(Config initialConfig);
}
