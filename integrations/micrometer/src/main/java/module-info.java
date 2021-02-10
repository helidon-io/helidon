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

module io.helidon.integrations.micrometer {

    requires java.logging;

    requires static java.annotation;

    requires static jakarta.activation;
    requires static jakarta.enterprise.cdi.api;
    requires static jakarta.inject.api;
    requires static jakarta.interceptor.api;

    requires io.helidon.common;
    requires io.helidon.common.servicesupport;
    requires io.helidon.common.servicesupport.cdi;
    requires io.helidon.config;
    requires io.helidon.webserver;
    requires io.helidon.webserver.cors;

    requires io.helidon.microprofile.config;
    requires io.helidon.microprofile.server;

    requires transitive micrometer.core;
    requires transitive micrometer.registry.prometheus;
    requires simpleclient;

    requires microprofile.config.api;

    exports io.helidon.integrations.micrometer;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.integrations.micrometer to weld.core.impl, io.helidon.microprofile.cdi;

    provides javax.enterprise.inject.spi.Extension with io.helidon.integrations.micrometer.MicrometerCdiExtension;
}