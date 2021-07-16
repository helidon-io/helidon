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

package io.helidon.examples.caching.memory;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.caching.Cache;
import io.helidon.common.reactive.Single;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

class CachingService implements Service {
    private final AtomicInteger counter = new AtomicInteger();
    private final Cache<Integer, String> simpleCache;
    private final Cache<Integer, String> loaderCache;

    CachingService(Cache<Integer, String> simpleCache, Cache<Integer, String> loaderCache) {
        this.simpleCache = simpleCache;
        this.loaderCache = loaderCache;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/", this::route);
    }

    private void route(ServerRequest request, ServerResponse response) {
        int id = Integer.parseInt(request.queryParams().first("id").orElseGet(this::key));

        simpleCache.computeSingle(id, this::producer)
                .flatMapSingle(result -> loaderCache.get(id)
                        .map(loaderResult -> "Simple cache: " + result + ", loader result: " + loaderResult))
                .forSingle(response::send);
    }

    private String key() {
        return String.valueOf(counter.get() % 3);
    }

    private Single<String> producer() {
        return Single.just(String.valueOf(counter.incrementAndGet()));
    }
}
