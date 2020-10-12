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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * An annotation making this test class a CDI bean with support for injection.
 * <p>
 * There is no need to provide {@code beans.xml} (actually it is not recommended, as it would combine beans
 * from all tests), instead use {@link io.helidon.microprofile.tests.junit5.AddBean},
 * {@link io.helidon.microprofile.tests.junit5.AddExtension}, and {@link io.helidon.microprofile.tests.junit5.AddConfig}
 * annotations to control the shape of the container.
 * <p>
 * To disable automated bean and extension discovery, annotate the class with
 * {@link io.helidon.microprofile.tests.junit5.DisableDiscovery}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(HelidonJunitExtension.class)
@Inherited
public @interface HelidonTest {
    /**
     * By default, CDI container is created once before the class is initialized and shut down
     * after. All test methods run within the same container.
     *
     * If this is set to {@code true}, a container is created per test method invocation.
     * This restricts the test in the following way:
     * 1. No injection into fields
     * 2. No injection into constructor
     *
     * @return whether to reset container per test method
     */
    boolean resetPerTest() default false;
}
