/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
 * MP Rest client.
 */
module io.helidon.microprofile.restclient {
    requires java.logging;
    requires transitive microprofile.rest.client.api;
    requires io.helidon.common.context;
    requires jersey.common;
    requires jersey.mp.rest.client;
    requires java.ws.rs;

    exports io.helidon.microprofile.restclient;

    opens io.helidon.microprofile.restclient to weld.core.impl, io.helidon.microprofile.cdi;

    provides org.eclipse.microprofile.rest.client.spi.RestClientListener
            with io.helidon.microprofile.restclient.MpRestClientListener;
    provides org.glassfish.jersey.internal.spi.ForcedAutoDiscoverable
            with  io.helidon.microprofile.restclient.HelidonRequestHeaderAutoDiscoverable;
}
