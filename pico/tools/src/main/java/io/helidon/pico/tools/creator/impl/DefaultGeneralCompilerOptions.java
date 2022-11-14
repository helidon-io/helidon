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

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import io.helidon.pico.tools.creator.CompilerOptions;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Implementation for {@link io.helidon.pico.tools.creator.CompilerOptions}.
 *
 * @see AbstractCreator
 */
//@AllArgsConstructor
//@Builder
@ToString
@EqualsAndHashCode
@Getter
@SuppressWarnings("unchecked")
public class DefaultGeneralCompilerOptions implements CompilerOptions {

    /*@Singular("classpathElement")*/ private final List<Path> classpath;
    /*@Singular("modulepathElement")*/ private final List<Path> modulepath;
    /*@Singular("sourcepathElement")*/ private final List<Path> sourcepath;
    /*@Singular("commandLineArgument")*/ private final List<String> commandLineArguments;

    /**
     * Compiler source version.
     */
    private final String source;

    /**
     * Compiler target version.
     */
    private final String target;

    protected DefaultGeneralCompilerOptions(DefaultGeneralCompilerOptionsBuilder builder) {
        this.classpath = Objects.isNull(builder.classpath)
                ? Collections.emptyList() : Collections.unmodifiableList(builder.classpath);
        this.modulepath = Objects.isNull(builder.modulepath)
                ? Collections.emptyList() : Collections.unmodifiableList(builder.modulepath);
        this.sourcepath = Objects.isNull(builder.sourcepath)
                ? Collections.emptyList() : Collections.unmodifiableList(builder.sourcepath);
        this.commandLineArguments = Objects.isNull(builder.commandLineArguments)
                ? Collections.emptyList() : Collections.unmodifiableList(builder.commandLineArguments);
        this.source = builder.source;
        this.target = builder.target;
    }

    public static DefaultGeneralCompilerOptionsBuilder builder() {
        return new DefaultGeneralCompilerOptionsBuilder() {};
    }


    public abstract static class DefaultGeneralCompilerOptionsBuilder {
        private List<Path> classpath;
        private List<Path> modulepath;
        private List<Path> sourcepath;
        private List<String> commandLineArguments;
        private String source;
        private String target;

        public DefaultGeneralCompilerOptions build() {
            return new DefaultGeneralCompilerOptions(this);
        }

        public DefaultGeneralCompilerOptionsBuilder classpath(Collection<Path> val) {
            this.classpath = Objects.isNull(val) ? null : new LinkedList<>(val);
            return this;
        }

        public DefaultGeneralCompilerOptionsBuilder classpath(Path val) {
            if (Objects.isNull(this.classpath)) {
                this.classpath = new LinkedList<>();
            }
            this.classpath.add(val);
            return this;
        }

        public DefaultGeneralCompilerOptionsBuilder modulepath(Collection<Path> val) {
            this.modulepath = Objects.isNull(val) ? null : new LinkedList<>(val);
            return this;
        }

        public DefaultGeneralCompilerOptionsBuilder sourcepath(Collection<Path> val) {
            this.sourcepath = Objects.isNull(val) ? null : new LinkedList<>(val);
            return this;
        }

        public DefaultGeneralCompilerOptionsBuilder commandLineArguments(Collection<String> val) {
            this.commandLineArguments = Objects.isNull(val) ? null : new LinkedList<>(val);
            return this;
        }

        public DefaultGeneralCompilerOptionsBuilder source(String val) {
            this.source = val;
            return this;
        }

        public DefaultGeneralCompilerOptionsBuilder target(String val) {
            this.target = val;
            return this;
        }
    }

}
