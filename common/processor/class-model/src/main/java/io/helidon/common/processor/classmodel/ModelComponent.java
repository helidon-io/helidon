/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.common.processor.classmodel;

import java.io.IOException;
import java.util.Set;

abstract class ModelComponent {

    private final boolean includeImport;

    ModelComponent(Builder<?, ?> builder) {
        this.includeImport = builder.includeImport;
    }

    abstract void writeComponent(ModelWriter writer,
                                 Set<String> declaredTokens,
                                 ImportOrganizer imports,
                                 ClassType classType) throws IOException;

    void addImports(ImportOrganizer.Builder imports) {
    }

    boolean includeImport() {
        return includeImport;
    }

    abstract static class Builder<B extends Builder<B, T>, T extends ModelComponent>
            implements io.helidon.common.Builder<B, T> {

        private boolean includeImport = true;

        Builder() {
        }

        /**
         * Whether to include import type information among the imports.
         *
         * @param includeImport whether to include imports
         * @return updated builder instance
         */
        public B includeImport(boolean includeImport) {
            this.includeImport = includeImport;
            return identity();
        }

    }

}
