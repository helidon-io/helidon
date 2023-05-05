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

package io.helidon.nima.webclient.http1;

import java.util.List;

import io.helidon.builder.Builder;
import io.helidon.builder.Singular;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.nima.http.media.MediaContext;
import io.helidon.nima.webclient.ClientConfig;
import io.helidon.nima.webclient.spi.WebClientService;

/**
 * Configuration of an HTTP/1.1 client.
 */
@Builder
interface Http1ClientConfig extends ClientConfig {
    MediaContext mediaContext();

    /**
     * Whether to use keep alive by default.
     *
     * @return {@code true} for keeping connections alive and re-using them for multiple requests (default), {@code false}
     *  to create a new connection for each request
     */
    @ConfiguredOption("true")
    boolean defaultKeepAlive();

    /**
     * Configure the maximum allowed size of the connection queue - the number of keep alive connections kept open at the same
     * time.
     *
     * @return maximum connection queue size
     */
    @ConfiguredOption("256")
    int connectionQueueSize();

    @ConfiguredOption("16384")
    int maxHeaderSize();

    @ConfiguredOption("256")
    int maxStatusLineLength();

    @ConfiguredOption("false")
    boolean sendExpectContinue();

    @ConfiguredOption("true")
    boolean validateHeaders();

    @ConfiguredOption("true")
    boolean servicesUseServiceLoader();

    @Singular
    List<WebClientService> services();
}
