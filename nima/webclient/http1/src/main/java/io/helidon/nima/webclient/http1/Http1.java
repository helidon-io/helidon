/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import io.helidon.nima.webclient.spi.Protocol;
import io.helidon.nima.webclient.spi.ProtocolConfig;

/**
 * Client protocol for HTTP/1.1.
 */
public final class Http1 {
    /**
     * Protocol to use when creating HTTP/1.1 clients.
     */
    public static final Protocol<Http1Client, Http1ClientProtocolConfig> PROTOCOL = Http1ProtocolProvider::new;

    private Http1() {
    }
}
