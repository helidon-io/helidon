/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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
 * Microprofile configuration module.
 */
module io.helidon.microprofile.security {
    requires java.logging;

    requires transitive io.helidon.security;
    requires io.helidon.security.providers.abac;
    requires transitive io.helidon.security.integration.jersey;
    requires transitive io.helidon.security.integration.webserver;
    requires io.helidon.microprofile.server;
    requires io.helidon.microprofile.cdi;
    requires jakarta.interceptor.api;

    exports io.helidon.microprofile.security;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.security to weld.core.impl, io.helidon.microprofile.cdi;

    provides javax.enterprise.inject.spi.Extension with io.helidon.microprofile.security.SecurityCdiExtension;
}
