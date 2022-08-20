/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.webserver;

import io.helidon.common.http.Http;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;

class CacheHeadersTest {


    @Test
    void normalCachingStrategy() {
        ServerResponse serverResponse = new ResponseTest.ResponseImpl(new ResponseTest.NoOpBareResponse(null));

        serverResponse.cachingStrategy(ServerResponse.CachingStrategy.NORMAL);

        assertThat("Cache-Control settings in response with normal caching",
                   serverResponse.headers().values(Http.Header.CACHE_CONTROL),
                   containsInAnyOrder("no-transform"));
    }

    @Test
    void noStrategy() {
        ServerResponse serverResponse = new ResponseTest.ResponseImpl(new ResponseTest.NoOpBareResponse(null));

        assertThat("Cache-Control settings in response with no caching strategy set",
                   serverResponse.headers().values(Http.Header.CACHE_CONTROL),
                   empty());
    }

    @Test
    void doNotCacheStrategy() {
        ServerResponse serverResponse = new ResponseTest.ResponseImpl(new ResponseTest.NoOpBareResponse(null));

        serverResponse.cachingStrategy(ServerResponse.CachingStrategy.NO_CACHING);

        assertThat("Cache-Control settings in response with normal caching",
                   serverResponse.headers().values(Http.Header.CACHE_CONTROL),
                   containsInAnyOrder("no-transform", "no-cache", "no-store", "must-revalidate"));
    }
}
