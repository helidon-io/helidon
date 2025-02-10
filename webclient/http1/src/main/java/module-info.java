/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
 * Helidon WebClient HTTP/1.1 Support.
 */
@Feature(value = "HTTP/1.1",
         description = "WebClient HTTP/1.1 support",
         in = HelidonFlavor.SE,
         path = {"WebClient", "HTTP/1.1"}
)
module io.helidon.webclient.http1 {

    requires io.helidon.builder.api; // @Builder - interfaces are a runtime dependency
    requires io.helidon.common.concurrency.limits;

    requires static io.helidon.common.features.api;

    requires transitive io.helidon.webclient.api;

    exports io.helidon.webclient.http1;

    provides io.helidon.webclient.spi.HttpClientSpiProvider
            with io.helidon.webclient.http1.Http1ClientSpiProvider;
    provides io.helidon.webclient.spi.ProtocolConfigProvider
            with io.helidon.webclient.http1.Http1ProtocolConfigProvider;

    uses io.helidon.common.concurrency.limits.spi.LimitProvider;
    uses io.helidon.webclient.spi.SourceHandlerProvider;

}