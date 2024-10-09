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

package io.helidon.testing;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.helidon.common.Weight;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.LazyConfigSource;
import io.helidon.service.registry.Service;

/**
 * Config source that is defined as a {@link io.helidon.config.spi.LazyConfigSource}, thus deferring lookup
 * until the latest possible moment in time.
 * <p>
 * This config source allows change in values after the config instance was created.
 * <p>
 * Note that this can only work if the key is requested AFTER the value is set on this config source, as Helidon
 * config does not support mutability (config is a snapshot except for {@link io.helidon.config.spi.LazyConfigSource}, which
 * whose results are still cached forever in the snapshot).
 * <p>
 * To use this config source:
 * <ul>
 *     <li>When using Service registry: look up this instance from the registry and set the values, before they are used
 *     by tests. Note that for example ServerTest will configure the local server endpoint under {@code test.server.port}</li>
 *     <li>When using manual configuration setup, add a new instance to the config builder.</li>
 * </ul>
 *
 * @see io.helidon.testing.TestConfig
 */
@Service.Provider
@Weight(1_000_000)
public class TestConfigSource implements ConfigSource, LazyConfigSource {
    private final Map<String, String> options = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    TestConfigSource() {
        TestConfig.register(this);
    }

    /**
     * Get a new instance without any configured keys.
     *
     * @return instance of a test config source.
     */
    public static TestConfigSource create() {
        return new TestConfigSource();
    }

    @Override
    public Optional<ConfigNode> node(String key) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(options.get(key))
                    .map(ConfigNode.ValueNode::create);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Set a value.
     *
     * @param key   key to set
     * @param value value to set
     */
    public void set(String key, String value) {
        lock.writeLock().lock();
        try {
            options.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clear all values.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            options.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
