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
import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class AnnotationFinderTest {

    @Test
    void testAnnotationFinder() throws Exception {
        Package pkg = Retry.class.getPackage();
        Method m = TimeoutAnnotBean.class.getMethod("timedRetry");
        AnnotationFinder finder = AnnotationFinder.create(pkg);
        Set<Class<? extends Annotation>> transitive = finder.findAnnotations(Set.of(m.getAnnotations()), null)
                .stream()
                .map(Annotation::annotationType)
                .collect(Collectors.toSet());
        assertThat(transitive, is(Set.of(CircuitBreaker.class, Retry.class, Timeout.class)));
    }
}
