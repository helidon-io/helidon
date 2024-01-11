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

package io.helidon.inject.tests.configbeans.driven.interceptor.test;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.inject.service.Injection;
import io.helidon.inject.tests.configbeans.FakeWebServer;

/**
 * This test case is applying {@link io.helidon.inject.service.Interception.Trigger} (an
 * {@link io.helidon.inject.service.Interception.Trigger})
 * on this class directly. Since it is a config-driven service it is forced to used the interface based approach to interceptors.
 */
// TODO: https://github.com/helidon-io/helidon/issues/6542
@TestInterceptorTrigger
@Injection.DrivenBy(ZImplConfigBlueprint.class)
@SuppressWarnings("ALL")
public class ZImpl implements IZ {
    public static final String HELIDON_INJECT__METHOD_0_ELEMENT = "";
    private final AtomicInteger postConstructCallCount = new AtomicInteger();

    @Injection.Inject
    ZImpl(ZImplConfig config) {
        assert (config != null && !config.name().isEmpty()) : Objects.toString(config);
    }

    @Injection.Inject
    ZImpl(Optional<FakeWebServer> fakeWebServer) {
        assert (fakeWebServer.isPresent());
    }

    @Override
    public String methodIZ1(String val) {
        return "methodIZ1:" + val;
    }

    @Injection.PostConstruct
    void postConstruct() {
        postConstructCallCount.incrementAndGet();
    }

    int postConstructCallCount() {
        return postConstructCallCount.get();
    }

}