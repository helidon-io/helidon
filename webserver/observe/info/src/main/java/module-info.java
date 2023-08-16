/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import io.helidon.webserver.observe.info.InfoObserveProvider;
import io.helidon.webserver.observe.spi.ObserveProvider;

/**
 * Helidon WebServer Observability Info Support.
 * Info allows configuration of custom properties to be available to users.
 * Info endpoint is unprotected by default and is available at {@code /observe/info} (configurable).
 */
module io.helidon.webserver.observe.info {
    requires io.helidon.config;
    requires transitive io.helidon.webserver.observe;
    requires io.helidon.webserver;
    requires io.helidon.http.media.jsonp;

    exports io.helidon.webserver.observe.info;

    provides ObserveProvider with InfoObserveProvider;
}