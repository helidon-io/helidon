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

package io.helidon.pico.builder.spi;

import io.helidon.pico.types.TypeName;

/**
 * The default implementation of {@link io.helidon.pico.builder.spi.TypeAndBody}.
 */
@SuppressWarnings("unchecked")
public class DefaultTypeAndBody implements TypeAndBody {

    private final TypeName typeName;
    private final String body;

    protected DefaultTypeAndBody(Builder builder) {
        this.typeName = builder.typeName;
        this.body = builder.body;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TypeName typeName() {
        return typeName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String body() {
        return body;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + toStringInner() + ")";
    }

    protected String toStringInner() {
        return "typeName=" + typeName()
                + ", body=" + body();
    }


    /**
     * Creates a new builder for this type.
     *
     * @return the fluent builder
     */
    public static Builder builder() {
        return new Builder();
    }


    /**
     * Builder for this type.
     */
    public static class Builder {
        private TypeName typeName;
        private String body;

        /**
         * Sets the typeName to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder typeName(TypeName val) {
            this.typeName = val;
            return this;
        }

        /**
         * Sets the body to val.
         *
         * @param val the value
         * @return this fluent builder
         */
        public Builder body(String val) {
            this.body = val;
            return this;
        }

        /**
         * Builds the instance.
         *
         * @return the built instance
         */
        public DefaultTypeAndBody build() {
            return new DefaultTypeAndBody(this);
        }
    }

}
