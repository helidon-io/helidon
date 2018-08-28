/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Environment variables config source.
 */
class MpcSourceEnvironmentVariables implements ConfigSource {
    private static final Pattern ENV_VAR = Pattern.compile("([A-Za-z0-9]+_?)*");

    private final Map<String, String> envVariables = new HashMap<>();

    MpcSourceEnvironmentVariables() {
        System.getenv().forEach((env, value) -> {
            if (env.contains("_") && !env.contains(".") && ENV_VAR.matcher(env).matches()) {
                // allow either MY_ENV_VAR or MY.ENV.VAR (only for keys that DO NOT contain a dot)
                String alternativeKey = env.replace('_', '.');
                envVariables.put(alternativeKey, value);

                // and allow my.env.var:
                alternativeKey = alternativeKey.toLowerCase();
                envVariables.put(alternativeKey, value);
            }
            envVariables.put(env, value);
        });
    }

    @Override
    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(envVariables);
    }

    @Override
    public String getValue(String propertyName) {
        return envVariables.get(propertyName);
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
        return Collections.unmodifiableSet(envVariables.keySet());
    }
}
