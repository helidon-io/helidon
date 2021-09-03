/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
 * CDI extension for microprofile config implementation.
 */
module io.helidon.microprofile.config {
    requires java.logging;
    requires jakarta.enterprise.cdi.api;
    requires jakarta.inject.api;
    requires io.helidon.common;
    requires io.helidon.config;
    requires transitive microprofile.config.api;
    requires io.helidon.config.mp;
    requires java.annotation;
    requires io.helidon.common.serviceloader;

    exports io.helidon.microprofile.config;

    // this is needed for CDI extensions that use non-public observer methods
    opens io.helidon.microprofile.config to weld.core.impl, io.helidon.microprofile.cdi;

    provides javax.enterprise.inject.spi.Extension with io.helidon.microprofile.config.ConfigCdiExtension;
}
