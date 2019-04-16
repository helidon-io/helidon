/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.Set;

import io.helidon.config.EnvironmentVariableAliases;

import org.eclipse.microprofile.config.spi.ConfigSource;

import static java.lang.System.getenv;

/**
 * Environment variables config source.
 */
class MpcSourceEnvironmentVariables implements ConfigSource {

    MpcSourceEnvironmentVariables() {
    }

    @Override
    public Map<String, String> getProperties() {
        return getenv();
    }

    @Override
    public String getValue(final String propertyName) {
        String result = getenv(propertyName);
        if (result == null) {
            for (final String alias : EnvironmentVariableAliases.aliasesOf(propertyName)) {
                result = getenv(alias);
                if (result != null) {
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public String getName() {
        return "helidon:environment-variables";
    }

    @Override
    public int getOrdinal() {
        return 300;
    }

    @Override
    public Set<String> getPropertyNames() {
        return getProperties().keySet();
    }
}
