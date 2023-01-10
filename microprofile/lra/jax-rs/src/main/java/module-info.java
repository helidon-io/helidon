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
import io.helidon.common.features.api.Preview;

/**
 * Helidon implementation of MicroProfile Long Running Actions.
 *
 * @see org.eclipse.microprofile.lra
 */
@Preview
@Feature(value = "Long Running Actions",
        description = "MicroProfile Long Running Actions",
        in = HelidonFlavor.MP,
        path = "LRA"
)
module io.helidon.microprofile.lra {
    requires static io.helidon.common.features.api;

    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.ws.rs;
    requires microprofile.lra.api;
    requires org.jboss.jandex;
    requires java.logging;
    requires jakarta.annotation;
    requires io.helidon.config;
    requires io.helidon.microprofile.config;
    requires io.helidon.microprofile.server;
    requires microprofile.config.api;
    requires jersey.common;
    requires io.helidon.lra.coordinator.client;
    requires io.helidon.common.reactive;

    uses io.helidon.lra.coordinator.client.CoordinatorClient;

    provides jakarta.enterprise.inject.spi.Extension with io.helidon.microprofile.lra.LraCdiExtension;
    provides org.glassfish.jersey.internal.spi.AutoDiscoverable with io.helidon.microprofile.lra.LraAutoDiscoverable;
}
