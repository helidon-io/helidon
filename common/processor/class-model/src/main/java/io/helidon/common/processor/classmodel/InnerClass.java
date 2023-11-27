/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

/**
 * Inner class model.
 *
 * @deprecated use {@code helidon-codegen-class-model} instead.
 */
@Deprecated(forRemoval = true, since = "4.1.0")
public final class InnerClass extends ClassBase {

    //Collected directly specified imports when building this class
    private final ImportOrganizer.Builder imports;

    private InnerClass(Builder builder) {
        super(builder);
        imports = ImportOrganizer.builder().from(builder.importOrganizer());
    }

    /**
     * Create new {@link Builder} instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    ImportOrganizer.Builder imports() {
        return imports;
    }

    /**
     * Fluent API builder for {@link InnerClass}.
     */
    public static final class Builder extends ClassBase.Builder<Builder, InnerClass> {

        private Builder() {
        }

        @Override
        public InnerClass build() {
            if (name() == null) {
                throw new ClassModelException("Class need to have name specified");
            }
            return new InnerClass(this);
        }

        @Override
        public Builder isStatic(boolean isStatic) {
            return super.isStatic(isStatic);
        }

    }
}
