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

package io.helidon.microprofile.faulttolerance;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.interceptor.InterceptorBinding;

/**
 * Searches for transitive annotations associated with interceptor bindings in
 * a given Java package. Some of these operations can be expensive, so their
 * results should be cached.
 *
 * For example, a new annotation {@code @TimedRetry} is itself annotated by
 * {@code @Timeout} and {@code @Retry}, and both need to be found if a method
 * uses {@code @TimedRetry} instead.
 */
public class AnnotationFinder {

    /**
     * Array of package prefixes we avoid traversing while computing
     * a transitive closure for an annotation.
     */
    private static final String[] SKIP_PACKAGE_PREFIXES = {
            "java.",
            "javax.",
            "jakarta.",
            "org.eclipse.microprofile."
    };

    private final Package pkg;

    private AnnotationFinder(Package pkg) {
        this.pkg = pkg;
    }

    /**
     * Create a find given a Java package.
     *
     * @param pkg a package
     * @return the finder
     */
    static AnnotationFinder create(Package pkg) {
        Objects.requireNonNull(pkg);
        return new AnnotationFinder(pkg);
    }

    Set<Annotation> findAnnotations(Set<Annotation> set, BeanManager bm) {
        return findAnnotations(set, new HashSet<>(), new HashSet<>(), pkg, bm);
    }

    /**
     * Collects a set of transitive annotations in a package. Follows any
     * annotation that has not been already seen (to avoid infinite loops)
     * and is of interest.
     *
     * @param set set of annotations to start with
     * @param result set of annotations returned
     * @param seen set of annotations already processed
     * @param pkg the package
     * @return the result set of annotations
     */
    private Set<Annotation> findAnnotations(Set<Annotation> set, Set<Annotation> result,
                                            Set<Annotation> seen, Package pkg, BeanManager bm) {
        for (Annotation a1 : set) {
            Class<? extends Annotation> a1Type = a1.annotationType();
            if (a1Type.getName().startsWith(pkg.getName())) {       // Avoid getPackage() - Issue #4296
                result.add(a1);
            } else if (!seen.contains(a1) && isOfInterest(a1, bm)) {
                seen.add(a1);
                Set<Annotation> a1Set = Set.of(a1Type.getAnnotations());
                findAnnotations(a1Set, result, seen, pkg, bm);
            }
        }
        return result;
    }

    private boolean isOfInterest(Annotation a, BeanManager bm) {
        if (bm != null && bm.isInterceptorBinding(a.annotationType())
                || a.annotationType().isAnnotationPresent(InterceptorBinding.class)) {
            Optional<String> matches = Stream.of(SKIP_PACKAGE_PREFIXES)
                    .filter(pp -> a.annotationType().getPackage().getName().startsWith(pp))
                    .findAny();
            return matches.isEmpty();
        }
        return false;
    }
}
