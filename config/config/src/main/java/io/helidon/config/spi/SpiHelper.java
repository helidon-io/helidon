/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config.spi;

final class SpiHelper {
    private SpiHelper() {
    }

    /**
     * Holder of singleton instance of {@link io.helidon.config.spi.ConfigNode.ObjectNode}.
     *
     * @see io.helidon.config.spi.ConfigNode.ObjectNode#empty()
     */
    static final class EmptyObjectNodeHolder {

        private EmptyObjectNodeHolder() {
            throw new AssertionError("Instantiation not allowed.");
        }

        /**
         * EMPTY singleton instance.
         */
        public static final ConfigNode.ObjectNode EMPTY = ConfigNode.ObjectNode.builder().build();
    }
}
