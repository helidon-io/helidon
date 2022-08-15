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

package io.helidon.nima.webserver.http;

import io.helidon.common.http.HttpPrologue;

/**
 * Routing request.
 */
public interface RoutingRequest extends ServerRequest {
    /**
     * Update path of this request.
     *
     * @param routedPath routed path, that provides matched path parameters from path pattern
     * @return this instance
     */
    RoutingRequest path(RoutedPath routedPath);

    /**
     * Update prologue of this request.
     *
     * @param newPrologue new prologue to use (on rerouting)
     * @return this instance
     */
    RoutingRequest prologue(HttpPrologue newPrologue);
}
