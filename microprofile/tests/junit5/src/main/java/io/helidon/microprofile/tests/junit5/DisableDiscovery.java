/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Whether discovery is automated or disabled. If discovery is desired, do not annotate test
 * class with this annotation.
 * <p>
 * When discovery is enabled, the whole classpath is scanned for bean archives (jar files containing
 * {@code META-INF/beans.xml}) and all beans and extensions are added automatically.
 * <p>
 * When discovery is disabled, CDI would only contain the CDI implementation itself and beans and extensions added
 * through annotations {@link io.helidon.microprofile.tests.junit5.AddBean} and
 * {@link io.helidon.microprofile.tests.junit5.AddExtension}
 *
 * If discovery is disabled on class level and desired on method level,
 * the value can be set to {@code false}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface DisableDiscovery {
    /**
     * By default if you annotate a class or a method, discovery gets disabled.
     * If you want to override configuration on method to differ from class, you
     * can configure the value to {@code false}, effectively enabling discovery.
     *
     * @return whether to disable discovery ({@code true}), or enable it ({@code false}). If this
     * annotation is not present, discovery is enabled
     */
    boolean value() default true;
}
