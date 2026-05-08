/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.packaging.se1;

import java.lang.System.Logger.Level;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.reactive.Single;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class MockOtlpService implements HttpService {
    private static final System.Logger LOGGER = System.getLogger(MockOtlpService.class.getName());

    private final AtomicReference<CompletableFuture<byte[]>> next = new AtomicReference<>(new CompletableFuture<>());

    @Override
    public void routing(HttpRules rules) {
        rules.post("/v1/traces", this::mockOtlp);
    }

    /**
     * Return completion being completed when the next OTLP export call arrives.
     *
     * @return completion being completed when the next trace export call arrives
     */
    Single<byte[]> next() {
        return Single.create(next.get());
    }

    private void mockOtlp(ServerRequest request, ServerResponse response) {
        byte[] entity = request.content().as(byte[].class);
        LOGGER.log(Level.INFO, "Received OTLP trace export payload of {0} bytes", entity.length);
        next.getAndSet(new CompletableFuture<>()).complete(entity);
        response.send();
    }
}
