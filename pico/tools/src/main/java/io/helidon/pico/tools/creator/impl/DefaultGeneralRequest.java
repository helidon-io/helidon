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

import java.util.Objects;

import lombok.Getter;
import lombok.ToString;

/**
 * Base for all request types.
 */
//@SuperBuilder(toBuilder = true)
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultGeneralRequest extends DefaultGeneralCodeGenNames {
    /*@Builder.Default*/ private final boolean analysisOnly/* = false*/;
    /*@Builder.Default*/ private final boolean failOnError/* = true*/;
    private final String generator;

    protected DefaultGeneralRequest(DefaultGeneralRequestBuilder builder) {
        super(builder);
        this.analysisOnly = builder.analysisOnly;
        this.failOnError = builder.failOnError;
        this.generator = builder.generator;
    }

    public static DefaultGeneralRequestBuilder<? extends DefaultGeneralRequest, ? extends DefaultGeneralRequestBuilder<?, ?>> builder() {
        return new DefaultGeneralRequestBuilder() {};
    }

    /**
     * Returns the generator, defaulting to the provided defaultGeneratorClassType if generator is not passed.
     *
     * @param defaultGeneratorClassType the default class type to use if generator is null
     * @return the generator name
     */
    public String getGenerator(Class<?> defaultGeneratorClassType) {
        String generator = getGenerator();
        return Objects.nonNull(generator) ? generator : defaultGeneratorClassType.getName();
    }


    public static abstract class DefaultGeneralRequestBuilder<C extends DefaultGeneralRequest, B extends DefaultGeneralRequestBuilder<C, B>>
            extends DefaultGeneralCodeGenNamesBuilder<C, B> {
        private boolean analysisOnly;
        private boolean failOnError = true;
        private String generator;

        public C build() {
            return (C) new DefaultGeneralRequest(this);
        }

        public B analysisOnly(boolean val) {
            this.analysisOnly = val;
            return (B) this;
        }

        public B failOnError(boolean val) {
            this.failOnError = val;
            return (B) this;
        }

        public B generator(String val) {
            this.generator = val;
            return (B) this;
        }
    }


}
