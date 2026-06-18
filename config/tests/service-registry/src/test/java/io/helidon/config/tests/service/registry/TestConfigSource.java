/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.tests.service.registry;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.Weight;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(200)
class TestConfigSource implements NodeConfigSource, PollableSource<Instant> {
    static final AtomicBoolean POLLING_STARTED = new AtomicBoolean();
    static final AtomicBoolean POLLING_STOPPED = new AtomicBoolean();

    private static final PollingStrategy POLLING_STRATEGY = new PollingStrategy() {
        @Override
        public void start(Polled polled) {
            POLLING_STARTED.set(true);
        }

        @Override
        public void stop() {
            POLLING_STOPPED.set(true);
        }
    };

    @Override
    public Optional<ConfigContent.NodeContent> load() throws ConfigException {
        return Optional.of(ConfigContent.NodeContent.builder()
                                   .node(ConfigNode.ObjectNode.builder()
                                                 .addValue("app.value", "source")
                                                 .build())
                                   .build());
    }

    @Override
    public boolean isModified(Instant stamp) {
        return false;
    }

    @Override
    public Optional<PollingStrategy> pollingStrategy() {
        return Optional.of(POLLING_STRATEGY);
    }
}
