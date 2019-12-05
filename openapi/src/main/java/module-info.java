/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

module io.helidon.openapi {
    requires java.logging;

    requires io.helidon.common;
    requires io.helidon.config;
    requires io.helidon.media.common;
    requires io.helidon.media.jsonp.server;
    requires io.helidon.webserver;

    requires jandex;

    requires smallrye.open.api;

    requires microprofile.openapi.api;

    exports io.helidon.openapi;
}
