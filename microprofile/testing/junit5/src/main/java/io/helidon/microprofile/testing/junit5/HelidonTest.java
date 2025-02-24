/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.helidon.common.testing.virtualthreads.PinningRecorder.DEFAULT_THRESHOLD;

/**
 * A shorthand to use {@link HelidonJunitExtension} with additional settings.
 * <p>
 * Sets the following defaults:
 * <ul>
 * <li>lifecycle: {@link TestInstance.Lifecycle#PER_CLASS}</li>
 * <li>method order: {@link HelidonImplicitResetOrderer}</li>
 * </ul>
 *
 * @see HelidonJunitExtension
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(HelidonJunitExtension.class)
@TestMethodOrder(HelidonImplicitResetOrderer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Inherited
public @interface HelidonTest {
    /**
     * Forces the CDI container to be initialized and shutdown for each test method.
     * <p>
     * The value of {@link org.junit.jupiter.api.TestInstance TestInstance} is ignored.
     *
     * @return whether to reset per test method
     */
    boolean resetPerTest() default false;

    /**
     * Time threshold for carrier thread blocking to be considered as pinning.
     *
     * @return threshold in milliseconds, {@code 20} is default
     */
    long pinningThreshold() default DEFAULT_THRESHOLD;

    /**
     * Whether to turn on pinning detection during {@code @HelidonTest}.
     *
     * @return true for turning detection on, {@code false} is default
     */
    boolean pinningDetection() default false;
}
