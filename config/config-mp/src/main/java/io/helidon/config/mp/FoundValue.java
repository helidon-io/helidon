/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.config.mp;

import org.eclipse.microprofile.config.spi.ConfigSource;

final class FoundValue {
    private final String propertyName;
    private final ConfigSource source;
    private final String rawValue;
    private final String resolvedValue;

    FoundValue(String propertyName,
               ConfigSource source,
               String rawValue,
               String resolvedValue) {
        this.propertyName = propertyName;
        this.source = source;
        this.rawValue = rawValue;
        this.resolvedValue = resolvedValue;
    }

    ConfigSource source() {
        return source;
    }

    String rawValue() {
        return rawValue;
    }

    String resolvedValue() {
        return resolvedValue;
    }

    String propertyName() {
        return propertyName;
    }
}
