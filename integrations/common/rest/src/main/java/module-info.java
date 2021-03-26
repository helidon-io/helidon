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

/**
 * Common classes for accessing JSON based REST APIs of third party systems.
 *
 * @see io.helidon.integrations.common.rest.RestApi
 * @see io.helidon.integrations.common.rest.ApiRequest
 * @see io.helidon.integrations.common.rest.ApiJsonRequest
 * @see io.helidon.integrations.common.rest.ApiEntityResponse
 * @see io.helidon.integrations.common.rest.ApiResponse
 */
module io.helidon.integrations.common.rest {
    requires java.logging;
    requires java.json;

    requires io.opentracing.api;

    requires io.helidon.common.http;
    requires io.helidon.common;
    requires io.helidon.common.reactive;
    requires io.helidon.faulttolerance;
    requires io.helidon.config;
    requires io.helidon.webclient;
    requires io.helidon.media.jsonp;

    exports io.helidon.integrations.common.rest;
}