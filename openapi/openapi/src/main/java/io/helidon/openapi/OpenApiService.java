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
package io.helidon.openapi;

import java.util.function.Function;

import io.helidon.common.config.NamedService;
import io.helidon.common.media.type.MediaType;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.webserver.http.HttpRules;

/**
 * OpenAPI service.
 */
public interface OpenApiService extends NamedService {

    /**
     * Test if the service should handle a request.
     *
     * @param headers headers
     * @return {@code true} if the service should handle the request
     */
    boolean supports(ServerRequestHeaders headers);

    /**
     * Set up the service.
     *
     * @param routing routing
     * @param docPath document context path
     * @param content content function
     */
    void setup(HttpRules routing, String docPath, Function<MediaType, String> content);
}
