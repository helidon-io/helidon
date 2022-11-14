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
import io.helidon.pico.tools.creator.ApplicationCreatorConfigOptions;
import io.helidon.pico.tools.creator.ApplicationCreatorRequest;

import lombok.Getter;
import lombok.ToString;

/**
 * Extensions of {@link DefaultGeneralCreatorRequest} to support {@link io.helidon.pico.Application}
 * creation.
 */
//@SuperBuilder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultApplicationCreatorRequest extends DefaultGeneralCreatorRequest implements ApplicationCreatorRequest {
    private final ApplicationCreatorCodeGen codeGenRequest;
    private final Msgr messager;
    /*@Builder.Default*/ private final ApplicationCreatorConfigOptions configOptions/* =
            new DefaultApplicationCreatorConfigOptions(ApplicationCreatorConfigOptions.PermittedProviderType.NONE, null)*/;

    protected DefaultApplicationCreatorRequest(DefaultApplicationCreatorRequestBuilder builder) {
        super(builder);
        this.codeGenRequest = builder.codeGenRequest;
        this.messager = builder.messager;
        this.configOptions = builder.configOptions;
    }

    public static DefaultApplicationCreatorRequestBuilder builder() {
        return new DefaultApplicationCreatorRequestBuilder() {};
    }


    public static abstract class DefaultApplicationCreatorRequestBuilder extends DefaultGeneralCreatorRequest.DefaultGeneralCreatorRequestBuilder {
        private ApplicationCreatorCodeGen codeGenRequest;
        private Msgr messager;
        private ApplicationCreatorConfigOptions configOptions;

        public DefaultApplicationCreatorRequest build() {
            return new DefaultApplicationCreatorRequest(this);
        }

        public DefaultApplicationCreatorRequestBuilder codeGenRequest(ApplicationCreatorCodeGen val) {
            this.codeGenRequest = val;
            return this;
        }

        public DefaultApplicationCreatorRequestBuilder messager(Msgr val) {
            this.messager = val;
            return this;
        }

        public DefaultApplicationCreatorRequestBuilder configOptions(ApplicationCreatorConfigOptions val) {
            this.configOptions = val;
            return this;
        }
    }

}
