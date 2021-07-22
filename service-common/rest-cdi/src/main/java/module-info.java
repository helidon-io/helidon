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
 * General-purpose reusable artifacts to help write CDI extensions, annotation processing, and interceptors for Helidon
 * services.
 */
module io.helidon.servicecommon.restcdi {

    requires jakarta.enterprise.cdi.api;
    requires io.helidon.servicecommon.rest;
    requires java.logging;
    requires microprofile.config.api;
    requires jakarta.interceptor.api;
    requires jakarta.inject.api;
    requires io.helidon.microprofile.server;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.servicecommon.restcdi to weld.core.impl, io.helidon.microprofile.cdi;

    exports io.helidon.servicecommon.restcdi;
}
