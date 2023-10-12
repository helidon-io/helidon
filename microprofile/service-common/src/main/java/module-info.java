/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
 * General-purpose reusable artifacts to help write CDI extensions, annotation processing, and interceptors for Helidon
 * services.
 */
module io.helidon.microprofile.servicecommon {

    requires io.helidon.config.mp;
    requires io.helidon.microprofile.server;
    requires io.helidon.servicecommon;
    requires jakarta.inject;
    requires microprofile.config.api;

    requires transitive jakarta.cdi;
    requires transitive jakarta.interceptor;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.servicecommon to weld.core.impl, io.helidon.microprofile.cdi;

    exports io.helidon.microprofile.servicecommon;

}