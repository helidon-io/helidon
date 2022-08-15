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

/**
 * HTTP filter.
 */
public interface Filter {
    /**
     * Handle a request.
     * Filter does not need to call {@link RoutingResponse#next()} - this is implied.
     * If filter calls any of the {@link RoutingResponse#send()} methods, the request will immediately terminate
     * and send a response.
     *
     * @param req routing request
     * @param res routing response
     */
    void handle(RoutingRequest req, RoutingResponse res);
}
