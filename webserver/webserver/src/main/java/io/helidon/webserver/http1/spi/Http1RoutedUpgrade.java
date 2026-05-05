/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.http1.spi;

import io.helidon.common.Api;

/**
 * A prepared HTTP/1 protocol upgrade that must run through HTTP request
 * filters and route policies before the protocol switch is completed.
 */
@Api.Internal
public interface Http1RoutedUpgrade {
    /**
     * Complete the upgrade after filters and route policies have allowed it.
     *
     * @param response response to use for the upgrade handshake
     * @return upgrade result
     */
    Http1UpgradeResult upgrade(Http1UpgradeResponse response);
}
