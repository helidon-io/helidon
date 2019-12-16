/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
 * CDI extension for microprofile jwt implementation.
 */
module io.helidon.microprofile.jwt.auth.cdi {
    requires java.logging;
    requires cdi.api;
    requires javax.inject;
    requires microprofile.config.api;
    requires java.ws.rs;

    requires transitive io.helidon.microprofile.jwt.auth;
    requires microprofile.jwt.auth.api;

    exports io.helidon.microprofile.jwt.auth.cdi;

    provides javax.enterprise.inject.spi.Extension with io.helidon.microprofile.jwt.auth.cdi.JwtAuthCdiExtension;
}
