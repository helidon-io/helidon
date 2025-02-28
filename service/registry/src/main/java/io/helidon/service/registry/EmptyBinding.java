/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.service.registry;

/**
 * An empty binding that configures nothing.
 * <p>
 * This is used as a base class for bindings generated during annotation processing, to be later replaced by
 * the {@code helidon-service-maven-plugin}.
 * <p>
 * Do not extend this class for custom binding implementations, as it will be ignored by some components,
 * as any instance that is an instance of this class will have special handling.
 */
public class EmptyBinding implements Binding {
    private static final System.Logger LOGGER = System.getLogger(EmptyBinding.class.getName());

    private final String name;

    /**
     * Create a new named binding. The name usually reflects the module, and the package of the application, though it is an
     * arbitrary string.
     *
     * @param name name of this binding
     */
    protected EmptyBinding(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void binding(DependencyPlanBinder binder) {
    }

    @Override
    public void configure(ServiceRegistryConfig.Builder builder) {
    }

    @Override
    public String toString() {
        return getClass().getName() + "{name=\"" + name + "\"}";
    }

    /**
     * Warns that an empty binding was generated and not overridden by the Service Maven Plugin.
     * The message references {@link Service.GenerateBinding}.
     */
    protected void warnEmpty() {
        LOGGER.log(System.Logger.Level.WARNING, "You have a class annotated with "
                + Service.GenerateBinding.class.getName() + " and this empty binding was generated for it. "
                + "Consider adding a build step to generate discovered binding. Binding name: " + name);
    }
}
