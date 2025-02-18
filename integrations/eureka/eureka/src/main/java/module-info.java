/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
 * Provides packages related to <dfn>service registration</dfn> in <a
 * href="https://github.com/Netflix/eureka/tree/v2.0.4">Eureka servers</a>.
 *
 * @see io.helidon.integrations.eureka.EurekaRegistrationFeature
 */
module io.helidon.integrations.eureka {

    requires transitive io.helidon.common.config;
    requires io.helidon.http;
    // requires io.helidon.http.encoding.gzip;
    requires transitive io.helidon.service.registry;
    requires io.helidon.webclient.http1;
    requires transitive io.helidon.webserver;
    requires transitive jakarta.json;

    provides io.helidon.webserver.http.HttpFeature with io.helidon.integrations.eureka.EurekaRegistrationFeature;

}
