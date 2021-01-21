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

import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;

class MpConfigValue implements ConfigValue {
    private final String name;
    private final String value;
    private final String rawValue;
    private final ConfigSource source;

    private MpConfigValue(String name, String value, String rawValue, ConfigSource source) {
        this.name = name;
        this.value = value;
        this.rawValue = rawValue;
        this.source = source;
    }

    public static MpConfigValue create(String propertyName) {
        return new MpConfigValue(propertyName, null, null, null);
    }

    public static MpConfigValue create(FoundValue foundValue) {
        return new MpConfigValue(foundValue.propertyName(),
                                 foundValue.resolvedValue(),
                                 foundValue.rawValue(),
                                 foundValue.source());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String getRawValue() {
        return rawValue;
    }

    @Override
    public String getSourceName() {
        if (source == null) {
            return null;
        }
        return source.getName();
    }

    @Override
    public int getSourceOrdinal() {
        if (source == null) {
            // see javadoc
            return 0;
        }
        return source.getOrdinal();
    }
}
