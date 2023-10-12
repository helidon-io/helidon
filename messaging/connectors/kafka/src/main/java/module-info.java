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

import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;

/**
 * Microprofile messaging Kafka connector.
 */
@Feature(value = "Kafka Connector",
        description = "Reactive messaging connector for Kafka",
        in = {HelidonFlavor.SE, HelidonFlavor.MP},
        path = {"Messaging", "Kafka"}
)
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.messaging.connectors.kafka {

    requires io.helidon.common.configurable;
    requires io.helidon.common.context;
    requires io.helidon.common.reactive;
    requires io.helidon.config.mp;
    requires io.helidon.messaging;
    requires java.security.jgss;// To allow KerberosLoginSubstitution
    requires java.security.sasl;
    requires microprofile.config.api;
    requires org.reactivestreams;

    requires static io.helidon.common.features.api;
    requires static jakarta.cdi;
    requires static jakarta.inject;
    requires static kafka.clients;
    requires static org.graalvm.nativeimage;

    requires transitive io.helidon.config;
    requires transitive microprofile.reactive.messaging.api;
    requires transitive microprofile.reactive.streams.operators.api;
    requires transitive org.slf4j;

    exports io.helidon.messaging.connectors.kafka;

}
