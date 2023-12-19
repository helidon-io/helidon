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

package io.helidon.inject.configdriven.tests.config;

import java.util.Objects;
import java.util.Optional;

import io.helidon.inject.configdriven.service.ConfigDriven;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

/**
 * For Testing.
 */
@ConfigDriven(value = FakeServerConfigBlueprint.class, activateByDefault = true)
public class FakeWebServer implements FakeWebServerContract {

    private final FakeServerConfig cfg;
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
        assert (!running);
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
