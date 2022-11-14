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

import io.helidon.pico.tools.creator.ApplicationCreatorCodeGen;
import io.helidon.pico.tools.creator.ApplicationCreatorResponse;

import lombok.Getter;
import lombok.ToString;

/**
 * Extensions of {@link DefaultGeneralCreatorResponse} to support {@link io.helidon.pico.Application}
 * creation.
 */
//@SuperBuilder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultApplicationCreatorResponse extends DefaultGeneralCreatorResponse implements ApplicationCreatorResponse {
    private final ApplicationCreatorCodeGen applicationCodeGenResponse;

    protected DefaultApplicationCreatorResponse(DefaultApplicationCreatorResponseBuilder builder) {
        super(builder);
        this.applicationCodeGenResponse = builder.applicationCodeGenResponse;
    }

    public static DefaultApplicationCreatorResponseBuilder builder() {
        return new DefaultApplicationCreatorResponseBuilder() {};
    }


    public static abstract class DefaultApplicationCreatorResponseBuilder extends DefaultGeneralCreatorResponse.DefaultGeneralCreatorResponseBuilder {
        private ApplicationCreatorCodeGen applicationCodeGenResponse;

        public DefaultApplicationCreatorResponse build() {
            return new DefaultApplicationCreatorResponse(this);
        }

        public DefaultApplicationCreatorResponseBuilder applicationCodeGenResponse(ApplicationCreatorCodeGen val) {
            this.applicationCodeGenResponse = val;
            return this;
        }
    }

}
