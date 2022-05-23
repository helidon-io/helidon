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
 *
 */
package io.helidon.servicecommon.rest;

import java.util.List;

import io.helidon.common.http.Http;
import io.helidon.webserver.ServerResponse;

/**
 * Common logic potentially useful from multiple REST services.
 */
public class RestServiceUtils {

    /**
     * Cache-Control settings to discourage caching.
     */
    public static final List<String> BUILT_IN_SERVICE_CACHE_CONTROL_SETTINGS = List.of("no-cache", "no-store", "must-revalidate");

    private RestServiceUtils() {
    }

    /**
     * Adds header to suppress caching of response.
     *
     * @param response the {@code ServerResponse} for which caching should be discouraged
     * @return updated response
     */
    public static ServerResponse discourageCaching(ServerResponse response) {
        response.addHeader(Http.Header.CACHE_CONTROL,
                           RestServiceUtils.BUILT_IN_SERVICE_CACHE_CONTROL_SETTINGS);
        return response;
    }
}
