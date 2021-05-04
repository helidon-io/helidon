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
 * Classes needed for OCI to connect to service API.
 */
module io.helidon.integrations.oci.connect {
    requires java.logging;

    //TODO remove again
    requires io.helidon.webserver;
    requires io.helidon.common;
    requires io.helidon.common.pki;
    requires io.helidon.faulttolerance;
    requires io.helidon.media.jsonp;
    requires io.helidon.security;
    requires io.helidon.security.providers.common;
    requires io.helidon.security.providers.httpsign;
    requires io.helidon.security.util;
    requires io.helidon.webclient.security;

    requires transitive java.json;

    requires transitive io.helidon.common.http;
    requires transitive io.helidon.common.reactive;
    requires transitive io.helidon.config;
    requires transitive io.helidon.integrations.common.rest;
    requires transitive io.helidon.webclient;

    exports io.helidon.integrations.oci.connect;
    exports io.helidon.integrations.oci.connect.spi;

    opens io.helidon.integrations.oci.connect to weld.core.impl, io.helidon.microprofile.cdi;
}