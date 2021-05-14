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
 *
 */

/**
 * Support for Micrometer in Helidon MP.
 */
module io.helidon.integrations.micrometer.cdi {

    requires java.logging;

    requires static java.annotation;

    requires static jakarta.activation;
    requires static jakarta.enterprise.cdi.api;
    requires static jakarta.inject.api;
    requires static jakarta.interceptor.api;

    requires io.helidon.common.http;
    requires io.helidon.servicecommon.rest;
    requires io.helidon.servicecommon.restcdi;
    requires io.helidon.config;
    requires io.helidon.webserver.cors;
    requires io.helidon.integrations.micrometer;

    requires micrometer.core;
    requires micrometer.registry.prometheus;
    requires simpleclient;

    exports io.helidon.integrations.micrometer.cdi;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.integrations.micrometer.cdi to weld.core.impl, io.helidon.microprofile.cdi;

    provides javax.enterprise.inject.spi.Extension with io.helidon.integrations.micrometer.cdi.MicrometerCdiExtension;
}
