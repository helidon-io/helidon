/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.cdi;

import java.util.LinkedList;
import java.util.List;

import io.helidon.config.Config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;

/**
 * Testing class.
 */
public class TestExtension implements Extension {
    static final String BUILD_TIME_START = "bti";
    static final String BUILD_TIME_END = "btd";
    static final String RUNTIME_INIT = "rti";
    static final String APPLICATION_INIT = "ai";
    static final String APPLICATION_BEFORE_DESTROYED = "abd";
    static final String APPLICATION_DESTROYED = "ad";

    private final List<String> events = new LinkedList<>();
    private Config runtimeConfig;

    // must be public so it works with java 11 (do not want to open this module to weld)
    public void registerBeans(@Observes BeforeBeanDiscovery bbd) {
        bbd.addAnnotatedType(TestBean.class, "unit-test-bean");
        bbd.addAnnotatedType(TestBean2.class, "unit-test-bean2");
    }

    public void buildTimeStart(@Observes @BuildTimeStart Object event) {
        events.add(BUILD_TIME_START);
    }

    public void buildTimeEnd(@Observes @BuildTimeEnd Object event) {
        events.add(BUILD_TIME_END);
    }

    public void runTimeInit(@Observes @RuntimeStart Config config) {
        events.add(RUNTIME_INIT);
        runtimeConfig = config;
    }

    public void applicationInit(@Observes @Initialized(ApplicationScoped.class) Object event) {
        events.add(APPLICATION_INIT);
    }

    public void applicationBeforeDestroyed(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event) {
        events.add(APPLICATION_BEFORE_DESTROYED);
    }

    public void applicationDestroyed(@Observes @Destroyed(ApplicationScoped.class) Object event) {
        events.add(APPLICATION_DESTROYED);
    }

    List<String> events() {
        return events;
    }

    Config runtimeConfig() {
        return runtimeConfig;
    }
}
