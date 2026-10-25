/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.service.gradle.plugin;

import javax.inject.Inject;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;

abstract class GenerateBindingsParameters extends BindingOptions implements WorkParameters {
    /**
     * Constructor used by Gradle managed instantiation.
     */
    @Inject
    public GenerateBindingsParameters() {
    }

    public abstract ConfigurableFileCollection getApplicationClasspath();

    public abstract ConfigurableFileCollection getSourceRoots();

    public abstract ConfigurableFileCollection getMainSourceRoots();

    public abstract ConfigurableFileCollection getTestSourceRoots();

    public abstract DirectoryProperty getClassesDirectory();

    public abstract DirectoryProperty getGeneratedSourceDirectory();

    public abstract ListProperty<String> getCompilerArgs();

    public abstract Property<String> getRelease();

    public abstract Property<String> getScope();
}
