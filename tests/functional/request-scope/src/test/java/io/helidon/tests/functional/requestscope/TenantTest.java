/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

package io.helidon.tests.functional.requestscope;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import io.helidon.faulttolerance.Async;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
class TenantTest {

    private static final int CONCURRENT_REQS = 50;

    @Inject
    private WebTarget baseTarget;

    @Test
    public void test() throws Exception {
        asyncCalls(() -> baseTarget.path("test").request()
                .header("x-tenant-id", "123").get(), null);
    }

    @Test
    @Disabled   // issue #8813
    public void test2() throws Exception {
        asyncCalls(() -> baseTarget.path("test2").request()
                .header("x-tenant-id", "123").get(), null);
    }

    @Test
    public void test3() throws Exception {
        asyncCalls(() -> baseTarget.path("test3").queryParam("param1", "1").request()
                .header("x-tenant-id", "123").get(), "1");
    }

    private void asyncCalls(Supplier<Response> supplier, String entityValue) throws Exception {
        CompletableFuture<?>[] futures = new CompletableFuture<?>[CONCURRENT_REQS];
        for (int i = 0; i < CONCURRENT_REQS; i++) {
            futures[i] = Async.create().invoke(supplier).toCompletableFuture();
        }
        CompletableFuture.allOf(futures).join();
        for (int i = 0; i < CONCURRENT_REQS; i++) {
            Response r = (Response) futures[i].get();
            assertThat(r.getStatus(), is(Status.OK.getStatusCode()));
            if (entityValue != null) {
                String value = r.readEntity(String.class);
                assertThat(entityValue, is(value));
            }
        }
    }

    @Test
    public void test4() {
        Response r;
        r = baseTarget.path("test4")
                .queryParam("param1", "1")
                .request()
                .get();
        assertThat(r.getStatus(), is(Status.OK.getStatusCode()));
        String entityValue = r.readEntity(String.class);
        assertThat(entityValue, is("1"));
    }

    @Test
    public void test5() {
        Response r;
        r = baseTarget.path("test5")
                .request()
                .header("x-tenant-id", "1")
                .get();
        assertThat(r.getStatus(), is(Status.OK.getStatusCode()));
        String entityValue = r.readEntity(String.class);
        assertThat(entityValue, is("1"));
    }

    @Test
    public void testStartup() {
        assertThat(StartupServices.SUCCESSFUL_STARTUP.get(), is(true));
    }
}
