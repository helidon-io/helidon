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

package io.helidon.webserver.tests;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.AfterStop;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class LifecycleMethodAnnotatedTest {
    private static final TestFeature FEATURE = new TestFeature();

    @SetUpRoute
    static void setUpRoute(HttpRouting.Builder http) {
        http.addFeature(FEATURE);
    }

    @AfterStop
    static void afterStop() {
        assertThat("Before start should only have been called on server startup", FEATURE.beforeStart.get(), is(1));
        assertThat("After stop should have been called on server stop", FEATURE.afterStop.get(), is(1));
    }

    @Test
    void testBeforeStartCalled(WebClient webClient) {
        ClientResponseTyped<String> request = webClient.get()
                .request(String.class);

        assertThat(request.entity(), is("works"));

        assertThat("Before start should have been called on server startup", FEATURE.beforeStart.get(), is(1));
        assertThat("After stop should not have been called on server startup", FEATURE.afterStop.get(), is(0));
    }

    private static final class TestFeature implements HttpFeature {
        final AtomicInteger beforeStart = new AtomicInteger();
        final AtomicInteger afterStop = new AtomicInteger();

        @Override
        public void setup(HttpRouting.Builder routing) {
            routing.get("/", (req, res) -> res.send("works"));
        }

        @Override
        public void beforeStart() {
            beforeStart.incrementAndGet();
        }

        @Override
        public void afterStop() {
            afterStop.incrementAndGet();
        }

        void reset() {
            beforeStart.set(0);
            afterStop.set(0);
        }
    }
}
