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

import java.io.File;
import java.util.Objects;

import io.helidon.pico.tools.creator.CodeGenPaths;
import io.helidon.pico.tools.processor.ServicesToProcess;

import lombok.Getter;
import lombok.ToString;

/**
 * Default implementation for {@link io.helidon.pico.tools.creator.CodeGenPaths}.
 */
//@AllArgsConstructor
//@Builder
@ToString
@Getter
@SuppressWarnings("unchecked")
public class DefaultGeneralCodeGenPaths implements CodeGenPaths {

    /**
     * Location for META-INF/services/.
     */
    public static final String META_INF_SERVICES = "META-INF/services/";

    private final String sourcePath;
    private final String outputPath;
    private final String generatedSourcesPath;
    /*@Builder.Default*/ private final String metaInfServicesPath/* = META_INF_SERVICES*/;
    private final String moduleInfoPath;

    protected DefaultGeneralCodeGenPaths(DefaultGeneralCodeGenPathsBuilder builder) {
        this.sourcePath = builder.sourcePath;
        this.outputPath = builder.outputPath;
        this.generatedSourcesPath = builder.generatedSourcesPath;
        this.metaInfServicesPath = builder.metaInfServicesPath;
        this.moduleInfoPath = builder.moduleInfoPath;
    }

    public static DefaultGeneralCodeGenPathsBuilder builder() {
        return new DefaultGeneralCodeGenPathsBuilder() {};
    }

    /**
     * Creates the {@link io.helidon.pico.tools.creator.impl.DefaultGeneralCodeGenPaths} given the current
     * batch of services to process.
     *
     * @param servicesToProcess the servies to process.
     * @return the payload for code gen paths.
     */
    public static DefaultGeneralCodeGenPaths toCodeGenPaths(ServicesToProcess servicesToProcess) {
        File moduleInfoFile = servicesToProcess.getLastGeneratedModuleInfoFile();
        if (Objects.isNull(moduleInfoFile)) {
            moduleInfoFile = servicesToProcess.getLastKnownModuleInfoFile();
        }

        DefaultGeneralCodeGenPaths codeGenPaths = DefaultGeneralCodeGenPaths.builder()
                .moduleInfoPath(Objects.nonNull(moduleInfoFile) ? moduleInfoFile.getPath() : null)
                .build();
        return codeGenPaths;
    }


    public abstract static class DefaultGeneralCodeGenPathsBuilder {
        private String sourcePath;
        private String outputPath;
        private String generatedSourcesPath;
        private String metaInfServicesPath = META_INF_SERVICES;
        private String moduleInfoPath;

        public DefaultGeneralCodeGenPaths build() {
            return new DefaultGeneralCodeGenPaths(this);
        }

        public DefaultGeneralCodeGenPathsBuilder sourcePath(String val) {
            this.sourcePath = val;
            return this;
        }

        public DefaultGeneralCodeGenPathsBuilder outputPath(String val) {
            this.outputPath = val;
            return this;
        }

        public DefaultGeneralCodeGenPathsBuilder generatedSourcesPath(String val) {
            this.generatedSourcesPath = val;
            return this;
        }

        public DefaultGeneralCodeGenPathsBuilder metaInfServicesPath(String val) {
            this.metaInfServicesPath = val;
            return this;
        }

        public DefaultGeneralCodeGenPathsBuilder moduleInfoPath(String val) {
            this.moduleInfoPath = val;
            return this;
        }
    }

}
