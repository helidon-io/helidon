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

import io.helidon.common.features.api.Aot;
import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

/**
 * GraphQL server integration with Helidon Reactive WebServer.
 */
@Preview
@Feature(value = "GraphQL",
         description = "GraphQL Support",
         since = "2.2.0",
         in = HelidonFlavor.SE,
         invalidIn = {HelidonFlavor.MP, HelidonFlavor.NIMA}
)
@Aot(description = "Incubating support, tested on limited use cases")
module io.helidon.reactive.graphql.server {

    requires io.helidon.common;
    requires io.helidon.common.uri;
    requires io.helidon.common.configurable;
    requires io.helidon.config;
    requires io.helidon.cors;
    requires io.helidon.graphql.server;
    requires io.helidon.reactive.media.common;
    requires io.helidon.reactive.media.jsonb;
    requires io.helidon.reactive.webserver;
    requires io.helidon.reactive.webserver.cors;
    requires static io.helidon.common.features.api;

    requires com.graphqljava;
    requires org.eclipse.yasson;

    exports io.helidon.reactive.graphql.server;
}