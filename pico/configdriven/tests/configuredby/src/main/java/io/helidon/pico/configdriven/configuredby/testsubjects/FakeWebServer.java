/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.configdriven.configuredby.testsubjects;

import java.util.Objects;
import java.util.Optional;

import io.helidon.builder.config.testsubjects.fakes.FakeServerConfig;
import io.helidon.builder.config.testsubjects.fakes.FakeTracer;
import io.helidon.pico.configdriven.ConfiguredBy;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

/**
 * For Testing.
 */
@ConfiguredBy(FakeServerConfig.class)
public class FakeWebServer implements FakeWebServerContract {

    private FakeServerConfig cfg;
    private boolean running;

    @Inject
    FakeWebServer(FakeServerConfig cfg,
                  Optional<FakeTracer> tracer) {
        this.cfg = Objects.requireNonNull(cfg);
        assert (tracer.isEmpty());
    }

    /**
     * For Testing.
     */
    @PostConstruct
    public void initialize() {
        running = true;
    }

    /**
     * For Testing.
     */
    @PreDestroy
    public void shutdown() {
        running = false;
    }

    @Override
    public FakeServerConfig configuration() {
        return cfg;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

}
