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
package io.helidon.microprofile.testing.testng;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static io.helidon.common.testing.virtualthreads.PinningRecorder.DEFAULT_THRESHOLD;

/**
 * A shorthand to use {@link HelidonTestNgListener} with additional settings.
 *
 * @see HelidonTestNgListener
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface HelidonTest {
    /**
     * Forces the CDI container to be initialized and shutdown for each test method.
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
