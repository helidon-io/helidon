/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.webclient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.http.DataChunk;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientException;
import io.helidon.webclient.WebClientResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

class ConnectionCloseTest extends TestParent {

    @Test
    void testCutConnection() throws ExecutionException, InterruptedException, TimeoutException {
        WebClient webClient = createNewClient();
        CompletableFuture<Throwable> actualErrorCf = new CompletableFuture<>();
        // Expecting WebClientException: Connection reset by the host
        webClient.get()
                .path("/connectionClose")
                .request()
                .flatMap(WebClientResponse::content)
                .map(DataChunk::bytes)
                .map(String::new)
                .log()
                .onError(actualErrorCf::complete)
                .ignoreElements();

        Throwable actual = actualErrorCf.get(10, TimeUnit.SECONDS);
        assertThat(actual, Matchers.instanceOf(WebClientException.class));
    }
}
