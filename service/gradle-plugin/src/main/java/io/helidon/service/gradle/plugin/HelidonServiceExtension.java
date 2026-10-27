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

/**
 * Gradle {@code helidonService} extension. Annotation processors must be configured separately
 * using Gradle's {@code annotationProcessor} and {@code testAnnotationProcessor} configurations.
 * This extension and its inherited options form the plugin's incubating build configuration API.
 * The JavaBean accessors and public constructor support Gradle managed properties and DSL decoration.
 */
@Api.Incubating
public abstract class HelidonServiceExtension extends BindingOptions {
    /**
     * Constructor for Gradle managed instantiation.
     */
    public HelidonServiceExtension() {
        getGenerateTestBinding().convention(true);
        getTestBindingClassName().convention("TestApplicationBinding");
    }

    /**
     * Whether to generate test bindings, default true. When false, tests use their compiler output
     * without a generated test binding.
     * @return test generation property
     */
    public abstract Property<Boolean> getGenerateTestBinding();

    /**
     * Test binding simple name, default {@code TestApplicationBinding}.
     * @return test binding class property
     */
    public abstract Property<String> getTestBindingClassName();

    /**
     * Test generated package, inferred from test and application sources when absent.
     * @return test package property
     */
    public abstract Property<String> getTestPackageName();
}
