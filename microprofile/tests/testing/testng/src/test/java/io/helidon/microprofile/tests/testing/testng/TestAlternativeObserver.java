/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.testing.testng;

import io.helidon.microprofile.testing.testng.AddBean;
import io.helidon.microprofile.testing.testng.HelidonTest;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@AddBean(TestAlternativeObserver.AlternativeObserver.class)
@HelidonTest
public class TestAlternativeObserver {

    @Inject
    Observer observer;

    @Test
    void doTest() {
        assertThat(observer.started, is(true));
    }

    @Singleton
    static class Observer {

        boolean started = false;

        void onStart(@Observes @Initialized(ApplicationScoped.class) Object ignore) {
            started = true;
        }
    }

    @Priority(0)
    @Alternative
    @Singleton
    static class AlternativeObserver extends Observer {
    }
}
