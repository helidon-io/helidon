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

import io.helidon.pico.tools.creator.ActivatorCreatorCodeGen;
import io.helidon.pico.tools.creator.ActivatorCreatorConfigOptions;
import io.helidon.pico.tools.creator.ActivatorCreatorRequest;
import io.helidon.pico.tools.creator.CodeGenPaths;
import io.helidon.pico.tools.creator.CompilerOptions;
import io.helidon.pico.tools.processor.ServicesToProcess;
import io.helidon.pico.types.TypeName;

import lombok.Getter;
import lombok.ToString;

/**
 * Default implementation for {@link io.helidon.pico.tools.creator.ActivatorCreatorRequest}.
 */
//@SuperBuilder(toBuilder = true)
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultActivatorCreatorRequest extends DefaultGeneralRequest implements ActivatorCreatorRequest {

    /*@Singular("serviceTypeName")*/ private final Collection<TypeName> serviceTypeNames;
    /*@Builder.Default*/ private final ActivatorCreatorConfigOptions configOptions/* =
            new DefaultActivatorCreatorConfigOptions(false, true, false)*/;
    private final ActivatorCreatorCodeGen codeGenRequest;
    private final CodeGenPaths codeGenPaths;
    private final CompilerOptions compilerOptions;

    protected DefaultActivatorCreatorRequest(DefaultActivatorCreatorRequestBuilder builder) {
        super(builder);
        this.serviceTypeNames = Objects.nonNull(builder.serviceTypeNames)
                ? Collections.unmodifiableCollection(builder.serviceTypeNames) : Collections.emptyList();
        this.configOptions = Objects.nonNull(builder.configOptions)
                ? builder.configOptions
                : new DefaultActivatorCreatorConfigOptions();
        this.codeGenRequest = builder.codeGenRequest;
        this.codeGenPaths = builder.codeGenPaths;
        this.compilerOptions = builder.compilerOptions;
    }

    public static DefaultActivatorCreatorRequestBuilder builder() {
        return new DefaultActivatorCreatorRequestBuilder() {};
    }

    /**
     * Create a request based upon the contents of {@link io.helidon.pico.tools.processor.ServicesToProcess}.
     *
     * @param servicesToProcess the batch being processed
     * @param codeGen           the code gen request
     * @param configOptions     the config options
     * @param failOnError       fail on error?
     * @return the activator request instance
     */
    public static DefaultActivatorCreatorRequest toActivatorCreatorRequest(ServicesToProcess servicesToProcess,
                                                                           ActivatorCreatorCodeGen codeGen,
                                                                           ActivatorCreatorConfigOptions configOptions,
                                                                           boolean failOnError) {
        String moduleName = servicesToProcess.determineGeneratedModuleName();
        String packageName = servicesToProcess.determineGeneratedPackageName();

        DefaultGeneralCodeGenPaths codeGenPaths = DefaultGeneralCodeGenPaths.toCodeGenPaths(servicesToProcess);

        DefaultActivatorCreatorRequest req = (DefaultActivatorCreatorRequest) DefaultActivatorCreatorRequest.builder()
                .serviceTypeNames(servicesToProcess.getServiceTypeNames())
                .codeGenRequest(codeGen)
                .codeGenPaths(codeGenPaths)
                .configOptions(configOptions)
                .failOnError(failOnError)
                .moduleName(moduleName)
                .packageName(packageName)
                .build();
        return req;
    }


    public static abstract class DefaultActivatorCreatorRequestBuilder extends DefaultGeneralRequest.DefaultGeneralRequestBuilder {
        private Collection<TypeName> serviceTypeNames;
        private ActivatorCreatorConfigOptions configOptions;
        private ActivatorCreatorCodeGen codeGenRequest;
        private CodeGenPaths codeGenPaths;
        private CompilerOptions compilerOptions;

        public DefaultActivatorCreatorRequest build() {
            return new DefaultActivatorCreatorRequest(this);
        }

        public DefaultActivatorCreatorRequestBuilder serviceTypeNames(Collection<TypeName> val) {
            this.serviceTypeNames = Objects.isNull(val) ? null : new LinkedList<>(val);
            return this;
        }

        public DefaultActivatorCreatorRequestBuilder configOptions(ActivatorCreatorConfigOptions val) {
            this.configOptions = val;
            return this;
        }

        public DefaultActivatorCreatorRequestBuilder codeGenRequest(ActivatorCreatorCodeGen val) {
            this.codeGenRequest = val;
            return this;
        }

        public DefaultActivatorCreatorRequestBuilder codeGenPaths(CodeGenPaths val) {
            this.codeGenPaths = val;
            return this;
        }

        public DefaultActivatorCreatorRequestBuilder compilerOptions(CompilerOptions val) {
            this.compilerOptions = val;
            return this;
        }
    }

}
