/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
 * Microprofile messaging JMS connector.
 */
@Preview
@Feature(value = "JMS Connector",
        description = "Reactive messaging connector for JMS",
        in = {HelidonFlavor.MP, HelidonFlavor.SE},
        path = {"Messaging", "JMS"}
)
@Aot(false)
module io.helidon.messaging.connectors.jms {
    requires static io.helidon.common.features.api;

    requires static jakarta.cdi;
    requires static jakarta.inject;
    requires jakarta.messaging;
    requires org.reactivestreams;
    requires transitive io.helidon.config;
    requires transitive microprofile.reactive.messaging.api;
    requires transitive microprofile.reactive.streams.operators.api;
    requires io.helidon.config.mp;
    requires io.helidon.common.context;
    requires io.helidon.common.reactive;
    requires io.helidon.common.configurable;
    requires io.helidon.messaging.jms.shim;
    requires transitive io.helidon.messaging;
    requires microprofile.config.api;
    requires java.naming;
    requires javax.jms.api;

    exports io.helidon.messaging.connectors.jms;
}
