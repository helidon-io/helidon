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

package io.helidon.webclient.http2;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webclient.api.HttpClientConfig;

/**
 * HTTP/2 full webclient configuration.
 */
@Prototype.Blueprint
interface Http2ClientConfigBlueprint extends HttpClientConfig, Prototype.Factory<Http2Client> {
    /**
     * HTTP/2 specific configuration.
     *
     * @return protocol specific configuration
     */
    @Option.Default("create()")
    Http2ClientProtocolConfig protocolConfig();
}
