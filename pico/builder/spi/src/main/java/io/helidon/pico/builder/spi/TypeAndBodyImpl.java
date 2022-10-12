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
public class TypeAndBodyImpl implements TypeAndBody {

    private final TypeName typeName;
    private final String body;

    protected TypeAndBodyImpl(Builder builder) {
        this.typeName = builder.typeName;
        this.body = builder.body;
    }

    @Override
    public TypeName getTypeName() {
        return typeName;
    }

    @Override
    public String getBody() {
        return body;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + toStringInner() + ")";
    }

    protected String toStringInner() {
        return "typeName=" + getTypeName()
                + ", body=" + getBody();
    }

    public static Builder<? extends Builder> builder() {
        return new Builder();
    }

    public static class Builder<B extends Builder<B>> {
        private TypeName typeName;
        private String body;

        public B typeName(TypeName val) {
            this.typeName = val;
            return (B) this;
        }

        public B body(String val) {
            this.body = val;
            return (B) this;
        }

        public TypeAndBodyImpl build() {
            return new TypeAndBodyImpl(this);
        }
    }

}
