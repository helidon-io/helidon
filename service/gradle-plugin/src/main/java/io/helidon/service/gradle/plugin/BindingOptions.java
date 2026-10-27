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

import io.helidon.common.Api;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Options shared by application and test binding generation.
 * These properties configure the {@link HelidonServiceExtension helidonService} extension and
 * each {@link GenerateBindings} task through its {@code options} property.
 * The JavaBean accessors and public constructor support Gradle managed properties and DSL decoration.
 */
@Api.Incubating
public abstract class BindingOptions {
    /**
     * Constructor for Gradle managed instantiation.
     */
    public BindingOptions() {
        getBindingClassName().convention("ApplicationBinding");
        getMainClassName().convention("ApplicationMain");
        getGenerateBinding().convention(true);
        getGenerateMain().convention(false);
        getValidate().convention(true);
        getFailOnError().convention(true);
        getFailOnWarning().convention(false);
    }

    /**
     * Generated package, inferred from application sources when absent.
     * @return package property
     */
    @Input
    @Optional
    public abstract Property<String> getPackageName();

    /**
     * Module name, inferred from module-info.java when absent.
     * @return module name property
     */
    @Input
    @Optional
    public abstract Property<String> getModuleName();

    /**
     * Binding class simple name, default {@code ApplicationBinding}.
     * @return binding class property
     */
    @Input
    public abstract Property<String> getBindingClassName();

    /**
     * Main class simple name, default {@code ApplicationMain}.
     * @return main class property
     */
    @Input
    public abstract Property<String> getMainClassName();

    /**
     * Whether to generate static bindings, default true.
     * @return generation property
     */
    @Input
    public abstract Property<Boolean> getGenerateBinding();

    /**
     * Whether to generate an application main, default false.
     * @return main generation property
     */
    @Input
    public abstract Property<Boolean> getGenerateMain();

    /**
     * Whether to validate the application, default true.
     * @return validation property
     */
    @Input
    public abstract Property<Boolean> getValidate();

    /**
     * Whether errors fail generation, default true.
     * @return error policy property
     */
    @Input
    public abstract Property<Boolean> getFailOnError();

    /**
     * Whether warnings fail generation, default false.
     * @return warning policy property
     */
    @Input
    public abstract Property<Boolean> getFailOnWarning();
}
