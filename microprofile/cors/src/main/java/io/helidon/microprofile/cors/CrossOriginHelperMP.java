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

package io.helidon.microprofile.cors;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceInfo;

import io.helidon.cors.CrossOrigin;

/**
 * Helper methods for MP CORS support.
 */
class CrossOriginHelperMP {

    private CrossOriginHelperMP() {
    }

    /**
     * Returns a {@code Supplier} for an {@code Optional<CrossOrigin>>} for the provided resource information (annotation).
     * <p>
     * We use a supplier because this code is needed only if there is no configuration for the specified resource. We can avoid
     * executing this unless we really need it.
     * </p>
     *
     * @param resourceInfo the information about the resource
     * @return Supplier for a CrossOrigin for the specified resource
     */
    static Supplier<Optional<CrossOrigin>> crossOriginFromAnnotationFinder(ResourceInfo resourceInfo) {

        return () -> {
            // If not found, inspect resource matched
            Method resourceMethod = resourceInfo.getResourceMethod();
            Class<?> resourceClass = resourceInfo.getResourceClass();

            CrossOrigin corsAnnot;
            OPTIONS optsAnnot = resourceMethod.getAnnotation(OPTIONS.class);
            if (optsAnnot != null) {
                corsAnnot = resourceMethod.getAnnotation(CrossOrigin.class);
            } else {
                Path pathAnnot = resourceMethod.getAnnotation(Path.class);
                Optional<Method> optionsMethod = Arrays.stream(resourceClass.getDeclaredMethods())
                        .filter(m -> {
                            OPTIONS optsAnnot2 = m.getAnnotation(OPTIONS.class);
                            if (optsAnnot2 != null) {
                                if (pathAnnot != null) {
                                    Path pathAnnot2 = m.getAnnotation(Path.class);
                                    return pathAnnot2 != null && pathAnnot.value()
                                            .equals(pathAnnot2.value());
                                }
                                return true;
                            }
                            return false;
                        })
                        .findFirst();
                corsAnnot = optionsMethod.map(m -> m.getAnnotation(CrossOrigin.class))
                        .orElse(null);
            }
            return Optional.ofNullable(corsAnnot);
        };
    }
}
