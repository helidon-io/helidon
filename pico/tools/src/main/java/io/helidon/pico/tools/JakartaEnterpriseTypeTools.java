/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tools;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.ServiceLoader;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;

/**
 * Conditionally based upon classpath, will handle {@code jakarta.enterprise}. If class support is not on
 * the classpath then there will be no service implementations available and will only resolve to this abstract class. If jakarta
 * enterprise is on the runtime classpath then the implementation will resolve.

 * @see JavaxTypeTools
 *
 * @deprecated
 */
public interface JakartaEnterpriseTypeTools {

    /**
     * Will be non-null if the classpath supports the ability to resolve jakarta enterprise annotations.
     */
    LazyValue<JakartaEnterpriseTypeTools> INSTANCE = LazyValue.create(() -> {
        try {
            return HelidonServiceLoader.create(
                    ServiceLoader.load(JakartaEnterpriseTypeTools.class,
                                       JakartaEnterpriseTypeTools.class.getClassLoader())).iterator().next();
        } catch (Throwable t) {
            return null;
        }
    });

    /**
     * Returns true if {@code jakarta.enterprise} is supported on the classpath.
     *
     * @return true if jakarta enterprise is supported on the classpath
     */
    default boolean isJakartaEnterpriseOnTheClasspath() {
        return false;
    }

    /**
     * Attempts to load the jakarta enterprise annotation type.
     *
     * @param annotationTypeName jakarta enterprise annotation name to attempt to resolve.
     * @return the class type if resolvable, else null
     */
    default Optional<Class<? extends Annotation>> loadAnnotationClass(
            String annotationTypeName) {
        return Optional.empty();
    }

}
