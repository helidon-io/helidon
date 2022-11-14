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

package io.helidon.pico.tools.creator.impl;

import io.helidon.pico.tools.creator.ActivatorCreatorConfigOptions;

import lombok.Getter;
import lombok.ToString;

/**
 * Represents configuration options for activator creation.
 */
//@AllArgsConstructor
//@Builder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultActivatorCreatorConfigOptions implements ActivatorCreatorConfigOptions {
    private final boolean supportsJsr330InStrictMode;
    /*@Builder.Default*/ private final boolean moduleCreated/* = true*/;
    private final boolean applicationPreCreated;

    public DefaultActivatorCreatorConfigOptions() {
        this(builder());
    }

    protected DefaultActivatorCreatorConfigOptions(DefaultActivatorCreatorConfigOptionsBuilder builder) {
        this.supportsJsr330InStrictMode = builder.supportsJsr330InStrictMode;
        this.moduleCreated = builder.moduleCreated;
        this.applicationPreCreated = builder.applicationPreCreated;
    }

    public static DefaultActivatorCreatorConfigOptionsBuilder builder() {
        return new DefaultActivatorCreatorConfigOptionsBuilder() {};
    }

    public static abstract class DefaultActivatorCreatorConfigOptionsBuilder {
        private boolean supportsJsr330InStrictMode;
        private boolean moduleCreated = true;
        private boolean applicationPreCreated;

        public DefaultActivatorCreatorConfigOptions build() {
            return new DefaultActivatorCreatorConfigOptions(this);
        }

        public DefaultActivatorCreatorConfigOptionsBuilder supportsJsr330InStrictMode(boolean val) {
            this.supportsJsr330InStrictMode = val;
            return this;
        }

        public DefaultActivatorCreatorConfigOptionsBuilder moduleCreated(boolean val) {
            this.moduleCreated = val;
            return this;
        }

        public DefaultActivatorCreatorConfigOptionsBuilder applicationPreCreated(boolean val) {
            this.applicationPreCreated = val;
            return this;
        }
    }
}
