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

package io.helidon.integrations.common.rest;

/**
 * Simple REST request.
 */
public final class RestRequest extends ApiRestRequest<RestRequest> {
    private RestRequest() {
    }

    /**
     * Create new request builder.
     *
     * @return new rest request
     */
    public static RestRequest builder() {
        return new RestRequest();
    }
}
