/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Disables CDI discovery.
 * <p>
 * If discovery is desired, do not annotate test class with this annotation.
 * <p>
 * If used on a method, the container will be reset regardless of the test lifecycle.
 * <p>
 * When disabling discovery, you are responsible for adding the beans and extensions needed to activate the features you need.
 * You can use the following annotations to do that:
 * <ul>
 *     <li>{@link AddBean} to add CDI beans</li>
 *     <li>{@link AddExtension} to add CDI extensions</li>
 *     <li>{@link AddJaxRs} a shorthand to add JAX-RS (Jersey)</li>
 * </ul>
 * <p>
 * See also the following "core" CDI extensions:
 * <ul>
 *     <li>{@link io.helidon.microprofile.server.ServerCdiExtension ServerCdiExtension} optional if using {@link AddJaxRs}</li>
 *     <li>{@link io.helidon.microprofile.server.JaxRsCdiExtension JaxRsCdiExtension} optional if using {@link AddJaxRs}</li>
 *     <li>{@link io.helidon.microprofile.config.ConfigCdiExtension ConfigCdiExtension}</li>
 * </ul>
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface DisableDiscovery {
    /**
     * By default, if you annotate a class or a method, discovery gets disabled.
     * If you want to override configuration on method to differ from class, you
     * can configure the value to {@code false}, effectively enabling discovery.
     *
     * @return whether to disable discovery ({@code true}), or enable it ({@code false}). If this
     *         annotation is not present, discovery is enabled
     */
    boolean value() default true;
}
