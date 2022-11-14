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

import lombok.Getter;
import lombok.ToString;

/**
 * Base for all response types.
 */
//@SuperBuilder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultGeneralResponse extends DefaultGeneralCodeGenNames {
    /*@Builder.Default*/ private final boolean success/* = true*/;
    private final Throwable error;

    protected DefaultGeneralResponse(DefaultGeneralResponseBuilder builder) {
        super(builder);
        this.success = builder.success;
        this.error = builder.error;
    }

    public static DefaultGeneralResponseBuilder<? extends DefaultGeneralResponse, ? extends DefaultGeneralResponseBuilder> builder() {
        return new DefaultGeneralResponseBuilder() { };
    }


    public abstract static class DefaultGeneralResponseBuilder<C extends DefaultGeneralCodeGenNames, B extends DefaultGeneralResponseBuilder<C, B>>
                    extends DefaultGeneralCodeGenNamesBuilder<C, B> {
        private boolean success = true;
        private Throwable error;

        public C build() {
            return (C) new DefaultGeneralCodeGenNames(this);
        }

        public B success(boolean val) {
            this.success = val;
            return (B) this;
        }

        public B error(Throwable val) {
            this.error = val;
            return (B) this;
        }
    }

}
