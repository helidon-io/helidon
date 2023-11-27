/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.configbeans.driven.application;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.inject.service.Injection;
import io.helidon.inject.tests.configbeans.FakeWebServerContract;
import io.helidon.inject.tests.configbeans.driven.configuredby.test.ASingletonServiceContract;

@Injection.Singleton
@Injection.RunLevel(Injection.RunLevel.STARTUP)
public class ASimpleRunLevelService {

    static int postConstructCount;
    static int preDestroyCount;
    private boolean running;
    private ASingletonServiceContract singleton;
    private List<Supplier<FakeWebServerContract>> fakeWebServers;

    @Injection.Inject // testing an empty/void ctor here
    public ASimpleRunLevelService() {
    }

    public static int getPostConstructCount() {
        return postConstructCount;
    }

    public static int getPreDestroyCount() {
        return preDestroyCount;
    }

    public static void reset() {
        postConstructCount = 0;
        preDestroyCount = 0;
    }

    @Injection.PostConstruct
    public void postConstruct() {
        assert (!running);
        Objects.requireNonNull(singleton);
        Objects.requireNonNull(fakeWebServers);
        running = true;
        postConstructCount++;
    }

    @Injection.PreDestroy
    public void preDestroy() {
        assert (running);
        Objects.requireNonNull(singleton);
        Objects.requireNonNull(fakeWebServers);
        preDestroyCount++;
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    @Injection.Inject
    void setSingleton(ASingletonServiceContract singleton) {
        assert (this.singleton == null);
        this.singleton = Objects.requireNonNull(singleton);
    }

    @Injection.Inject
    void setWebServer(List<Supplier<FakeWebServerContract>> fakeWebServers) {
        assert (this.fakeWebServers == null);
        assert (!Objects.requireNonNull(fakeWebServers).isEmpty());
        this.fakeWebServers = Objects.requireNonNull(fakeWebServers);
    }

}
