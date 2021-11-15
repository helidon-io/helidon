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
package io.helidon.microprofile.config;

import org.eclipse.microprofile.config.ConfigValue;

class DefaultConfigValue implements ConfigValue {
    private final String key;
    private final String value;

    DefaultConfigValue(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String getName() {
        return key;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String getRawValue() {
        return value;
    }

    @Override
    public String getSourceName() {
        return null;
    }

    @Override
    public int getSourceOrdinal() {
        return 0;
    }
}
