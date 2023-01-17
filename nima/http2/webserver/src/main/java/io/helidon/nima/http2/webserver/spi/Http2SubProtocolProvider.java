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

package io.helidon.nima.http2.webserver.spi;

import io.helidon.config.Config;

/**
 * {@link java.util.ServiceLoader} provider interface for HTTP/2 sub-protocols.
 */
public interface Http2SubProtocolProvider {

    /**
     * Provider's specific configuration node name.
     *
     * @return name of the node to request
     */
    String configKey();

    /**
     * Creates an instance of HTTP/2 sub-protocol selector.
     *
     * @param config {@link io.helidon.config.Config} configuration node located on the node returned by {@link #configKey()}
     * @return new HTTP/2 sub-protocol selector
     */
    Http2SubProtocolSelector create(Config config);

}
