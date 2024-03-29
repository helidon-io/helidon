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

package io.helidon.inject.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.inject.api.ActivationResult;
import io.helidon.inject.api.Bootstrap;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.Services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class LockContentionTest {
    final int COUNT = 100;

    final ExecutorService es = Executors.newFixedThreadPool(16);
    final Config config = Config.builder(
                    ConfigSources.create(
                            Map.of("inject.permits-dynamic", "true"), "config-1"))
            .disableEnvironmentVariablesSource()
            .disableSystemPropertiesSource()
            .build();

    @BeforeEach
    void init() {
        InjectionServices.globalBootstrap(Bootstrap.builder().config(config).build());
    }

    @AfterEach
    void tearDown() {
        SimpleInjectionTestingSupport.resetAll();
    }

    @Test
    // we cannot interlace shutdown with startups here - so instead we are checking to ensure N threads can call startup
    // and then N threads can call shutdown in parallel, but in phases
    void lockContention() {
        Map<String, Future<?>> result = new ConcurrentHashMap<>();
        for (int i = 1; i <= COUNT; i++) {
            result.put("start run:" + i, es.submit(this::start));
        }

        verify(result);
        result.clear();

        for (int i = 1; i <= COUNT; i++) {
            result.put("shutdown run:" + i, es.submit(this::shutdown));
        }

        verify(result);
    }

    void verify(Map<String, Future<?>> result) {
        result.forEach((k, v) -> {
            try {
                assertThat(k, v.get(), notNullValue());
            } catch (Exception e) {
                fail("failed on " + k, e);
            }
        });
    }

    Services start() {
        return Objects.requireNonNull(InjectionServices.realizedServices());
    }

    Map<TypeName, ActivationResult> shutdown() {
        InjectionServices injectionServices = InjectionServices.injectionServices().orElseThrow();
        Map<TypeName, ActivationResult> result = new LinkedHashMap<>();
        Map<TypeName, ActivationResult> round;
        do {
            round = injectionServices.shutdown().orElseThrow();
            result.putAll(round);
        } while (!round.isEmpty());
        return result;
    }

}
