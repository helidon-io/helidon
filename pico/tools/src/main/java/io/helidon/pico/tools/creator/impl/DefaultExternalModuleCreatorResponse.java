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

import io.helidon.pico.tools.creator.ActivatorCreatorRequest;
import io.helidon.pico.tools.creator.ExternalModuleCreatorResponse;

import lombok.Getter;
import lombok.ToString;

/**
 * Default implementation for {@link io.helidon.pico.tools.creator.ExternalModuleCreatorResponse}.
 */
//@SuperBuilder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultExternalModuleCreatorResponse extends DefaultGeneralCreatorResponse implements ExternalModuleCreatorResponse {
    private final ActivatorCreatorRequest activatorCreatorRequest;

    protected DefaultExternalModuleCreatorResponse(DefaultExternalModuleCreatorResponseBuilder builder) {
        super(builder);
        this.activatorCreatorRequest = builder.activatorCreatorRequest;
    }

    public static DefaultExternalModuleCreatorResponseBuilder builder() {
        return new DefaultExternalModuleCreatorResponseBuilder() {};
    }

    public static abstract class DefaultExternalModuleCreatorResponseBuilder extends DefaultGeneralCreatorResponse.DefaultGeneralCreatorResponseBuilder {
        private ActivatorCreatorRequest activatorCreatorRequest;

        public DefaultExternalModuleCreatorResponse build() {
            return new DefaultExternalModuleCreatorResponse(this);
        }

        public DefaultExternalModuleCreatorResponseBuilder activatorCreatorRequest(ActivatorCreatorRequest val) {
            this.activatorCreatorRequest = val;
            return this;
        }
    }

}
