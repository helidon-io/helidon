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

import io.helidon.caching.Cache;
import io.helidon.caching.CacheConfig;
import io.helidon.caching.CacheManager;
import io.helidon.caching.SimpleLoader;
import io.helidon.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

public class Main {
    public static void main(String[] args) {
        Config config = Config.create();

        CacheManager cacheManager = CacheManager.create(config.get("caching"));
        Cache<Integer, String> simpleCache = cacheManager
                .<Integer, String>cache("simple-cache")
                .await();
        Cache<Integer, String> loaderCache = cacheManager
                .<Integer, String>cache("loader-cache", CacheConfig.create(SimpleLoader.create(key -> "v-" + key)))
                .await();

        WebServer webServer = WebServer.builder()
                .config(config.get("server"))
                .routing(Routing.builder()
                                 .register("/cache", new CachingService(simpleCache, loaderCache))
                                 .build())
                .build()
                .start()
                .await();
    }
}
