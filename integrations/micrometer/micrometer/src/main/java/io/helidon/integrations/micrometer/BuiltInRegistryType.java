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

package io.helidon.integrations.micrometer;

import java.util.Locale;

/**
 * Available built-in registry types.
 */
public enum BuiltInRegistryType {

    /**
     * Prometheus built-in registry type.
     */
    PROMETHEUS;

    /**
     * Describes an unrecognized built-in registry type.
     */
    public static class UnrecognizedBuiltInRegistryTypeException extends Exception {

        private static final long serialVersionUID = 9079876961827144352L;
        private final String unrecognizedType;

        /**
         * Creates a new instance of the exception.
         *
         * @param unrecognizedType the unrecognized type
         */
        public UnrecognizedBuiltInRegistryTypeException(String unrecognizedType) {
            super();
            this.unrecognizedType = unrecognizedType;
        }

        /**
         * Returns the unrecognized type.
         *
         * @return the unrecognized type
         */
        public String unrecognizedType() {
            return unrecognizedType;
        }

        @Override
        public String getMessage() {
            return "Unrecognized built-in Micrometer registry type: " + unrecognizedType;
        }
    }

    public static BuiltInRegistryType valueByName(String name) throws UnrecognizedBuiltInRegistryTypeException {
        try {
            return valueOf(name.trim()
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnrecognizedBuiltInRegistryTypeException(name);
        }
    }
}