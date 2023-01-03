/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
 * Helidon Kotlin Extensions Support
 */
module io.helidon.integrations.kotlin.support.reactive {

    requires io.helidon.reactive.dbclient;
    requires io.helidon.reactive.health;
    requires io.helidon.reactive.media.jsonp;
    requires io.helidon.reactive.servicecommon;
    requires io.helidon.reactive.webserver.cors;
    requires io.helidon.reactive.webserver.jersey;
    requires io.helidon.reactive.webserver;
    requires io.helidon.security.providers.oidc.common;

    requires java.logging;

    requires kotlin.stdlib;

    exports io.helidon.integrations.kotlin.support.reactive;
}
