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

package io.helidon.pico.configdriven.configuredby.application.test;

import java.util.List;
import java.util.Objects;

import io.helidon.pico.RunLevel;
import io.helidon.pico.configdriven.configuredby.test.ASingletonServiceContract;
import io.helidon.pico.configdriven.configuredby.test.FakeWebServerContract;
import io.helidon.pico.spi.Resetable;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
@RunLevel(RunLevel.STARTUP)
public class ASimpleRunLevelService implements Resetable {

    static int postConstructCount;
    static int preDestroyCount;
    private boolean running;
    private ASingletonServiceContract singleton;
    private List<Provider<FakeWebServerContract>> fakeWebServers;

    @Inject // testing an empty/void ctor here
    public ASimpleRunLevelService() {
    }

    @Inject
    void setSingleton(
            ASingletonServiceContract singleton) {
        assert (this.singleton == null);
        this.singleton = Objects.requireNonNull(singleton);
    }

    @Inject
    void setWebServer(
            List<Provider<FakeWebServerContract>> fakeWebServers) {
        assert (this.fakeWebServers == null);
        assert (!Objects.requireNonNull(fakeWebServers).isEmpty());
        this.fakeWebServers = Objects.requireNonNull(fakeWebServers);
    }

    @Override
    public boolean reset(
            boolean deep) {
        postConstructCount = 0;
        preDestroyCount = 0;
        return true;
    }

    @PostConstruct
    public void postConstruct() {
        assert (!running);
        Objects.requireNonNull(singleton);
        Objects.requireNonNull(fakeWebServers);
        running = true;
        postConstructCount++;
    }

    @PreDestroy
    public void preDestroy() {
        assert (running);
        Objects.requireNonNull(singleton);
        Objects.requireNonNull(fakeWebServers);
        preDestroyCount++;
        running = false;
    }

    public static int getPostConstructCount() {
        return postConstructCount;
    }

    public static int getPreDestroyCount() {
        return preDestroyCount;
    }

    public boolean isRunning() {
        return running;
    }

}
