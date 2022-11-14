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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Objects;

import io.helidon.pico.tools.creator.CodeGenPaths;
import io.helidon.pico.tools.creator.CompilerOptions;
import io.helidon.pico.tools.creator.GeneralCreatorRequest;
import io.helidon.pico.types.TypeName;

import lombok.Getter;
import lombok.ToString;

/**
 * Extensions to {@link DefaultGeneralRequest} supporting specifics for codegen related work.
 */
//@SuperBuilder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultGeneralCreatorRequest extends DefaultGeneralRequest implements GeneralCreatorRequest {
    /*@Singular("serviceTypeName")*/ private final Collection<TypeName> serviceTypeNames;
    private final CodeGenPaths codeGenPaths;
    private final CompilerOptions compilerOptions;

    protected DefaultGeneralCreatorRequest(DefaultGeneralCreatorRequestBuilder builder) {
        super(builder);

        this.serviceTypeNames = Objects.nonNull(builder.serviceTypeNames)
                ? Collections.unmodifiableCollection(builder.serviceTypeNames) : null;
        this.codeGenPaths = builder.codeGenPaths;
        this.compilerOptions = builder.compilerOptions;
    }


    public static abstract class DefaultGeneralCreatorRequestBuilder extends DefaultGeneralRequest.DefaultGeneralRequestBuilder {
        private Collection<TypeName> serviceTypeNames;
        private CodeGenPaths codeGenPaths;
        private CompilerOptions compilerOptions;

        public DefaultGeneralCreatorRequest build() {
            return new DefaultGeneralCreatorRequest(this);
        }

        public DefaultGeneralCreatorRequestBuilder serviceTypeNames(Collection<TypeName> val) {
            this.serviceTypeNames = Objects.isNull(val) ? null : new LinkedList<>(val);
            return this;
        }

        public DefaultGeneralCreatorRequestBuilder codeGenPaths(CodeGenPaths val) {
            this.codeGenPaths = val;
            return this;
        }

        public DefaultGeneralCreatorRequestBuilder compilerOptions(CompilerOptions val) {
            this.compilerOptions = val;
            return this;
        }
    }

}
