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

package io.helidon.webserver.hsts;

import java.time.Duration;

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;

final class HstsHeader {
    private HstsHeader() {
    }

    static Header create(HstsFeatureConfig config) {
        Duration maxAge = config.maxAge();
        if (maxAge.isNegative()) {
            throw new IllegalArgumentException("HSTS max-age must not be negative");
        }

        StringBuilder value = new StringBuilder("max-age=").append(maxAge.toSeconds());
        if (config.includeSubDomains()) {
            value.append("; includeSubDomains");
        }
        if (config.preload()) {
            value.append("; preload");
        }

        return HeaderValues.create(HeaderNames.STRICT_TRANSPORT_SECURITY, value.toString());
    }
}
