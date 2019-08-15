/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
 * Security integration with Jersey.
 */
module io.helidon.security.integration.jersey {
    requires java.logging;
    requires java.annotation;

    requires transitive io.helidon.security;
    requires transitive io.helidon.security.annotations;
    requires transitive io.helidon.security.providers.common;
    requires transitive io.helidon.security.util;
    requires transitive io.helidon.common.serviceloader;
    requires transitive java.ws.rs;

    requires io.helidon.common.context;
    requires io.helidon.jersey.common;
    requires io.helidon.security.integration.common;
    requires io.helidon.webclient.jaxrs;
    requires jersey.common;
    requires jersey.server;
    requires jersey.client;
    requires javax.inject;

    exports io.helidon.security.integration.jersey;

    // needed for jersey injection
    opens io.helidon.security.integration.jersey to hk2.locator,hk2.utils;

    uses io.helidon.security.providers.common.spi.AnnotationAnalyzer;
}
