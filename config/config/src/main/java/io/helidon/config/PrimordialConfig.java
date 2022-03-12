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

package io.helidon.config;

import java.util.Objects;

/**
 * Resolve config solely using system and env variables.
 * TODO: discuss with Tomas
 */
public class PrimordialConfig {

    /**
     * Searches system property, followed by env for a key value.
     *
     * @param name          the name of the property to search for (required)
     * @param defaultVal    the default value to return if the name is not found
     *
     * @return the value if found else the default value
     */
    public static String getProp(String name, String defaultVal) {
        String val = System.getProperty(name);
        if (val == null) {
            val = System.getenv(name);
        }
        if (Objects.isNull(val)) {
            val = defaultVal;
        }
        if (Objects.nonNull(val)) {
            val = val.replaceFirst("^~", System.getProperty("user.home"));
        }
        return val;
    }

    /**
     * Searches system property, followed by env for a key value.
     *
     * @param name          the name of the property to search for (required)
     * @param defaultVal    the default value to return if the name is not found
     *
     * @return the value if found else the default value
     */
    public static boolean getProp(String name, boolean defaultVal) {
        return Boolean.parseBoolean(getProp(name, String.valueOf(defaultVal)));
    }

    /**
     * Searches system property, followed by env for a key value.
     *
     * @param name          the name of the property to search for (required)
     * @param defaultVal    the default value to return if the name is not found
     *
     * @return the value if found else the default value
     */
    public static int getProp(String name, int defaultVal) {
        return Integer.parseInt(getProp(name, String.valueOf(defaultVal)));
    }

}
