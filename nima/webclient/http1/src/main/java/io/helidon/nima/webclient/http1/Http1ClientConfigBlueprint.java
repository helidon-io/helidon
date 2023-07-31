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

import io.helidon.builder.api.Prototype;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.nima.webclient.api.HttpClientConfig;

/**
 * HTTP/1.1. full webclient configuration.
 */
@Prototype.Blueprint
interface Http1ClientConfigBlueprint extends HttpClientConfig, Prototype.Factory<Http1Client> {
    /**
     * HTTP/1.1 specific configuration.
     *
     * @return protocol specific configuration
     */
    @ConfiguredOption("create()")
    Http1ClientProtocolConfig protocolConfig();
}
