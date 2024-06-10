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
package io.helidon.codegen.classmodel;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import io.helidon.common.types.TypeName;

/**
 * Objects which describes return type configuration.
 */
public final class Returns extends DescribableComponent {

    private Returns(Builder builder) {
        super(builder);
    }

    /**
     * Return new {@link Builder} instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    void writeComponent(ModelWriter writer, Set<String> declaredTokens, ImportOrganizer imports, ClassType classType)
            throws IOException {
        String typeName = imports.typeName(type(), includeImport());
        writer.write(typeName);
    }

    /**
     * Fluent API builder for {@link Returns}.
     */
    public static final class Builder extends DescribableComponent.Builder<Builder, Returns> {

        private Builder() {
        }

        @Override
        public Returns build() {
            return new Returns(this);
        }

        @Override
        public Builder type(String type) {
            return super.type(type);
        }

        @Override
        public Builder type(Class<?> type) {
            return super.type(type);
        }

        @Override
        public Builder type(TypeName type) {
            return super.type(type);
        }

        @Override
        public Builder description(String description) {
            return super.description(description);
        }

        @Override
        public Builder description(List<String> description) {
            return super.description(description);
        }
    }

}
