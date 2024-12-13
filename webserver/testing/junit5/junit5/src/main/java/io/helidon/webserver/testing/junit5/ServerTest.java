/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import static io.helidon.common.testing.virtualthreads.PinningRecorder.DEFAULT_THRESHOLD;

/**
 * Test of server that opens a socket (for integration tests).
 * Can be used together with:
 * <ul>
 *     <li>{@link SetUpRoute}</li>
 *     <li>{@link SetUpServer}</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(HelidonServerJunitExtension.class)
@Inherited
public @interface ServerTest {
    /**
     * Time threshold for carrier thread blocking to be considered as pinning.
     *
     * @return threshold in milliseconds, {@code 20} is default
     */
    long pinningThreshold() default DEFAULT_THRESHOLD;

    /**
     * Whether to turn on pinning detection during test.
     *
     * @return true for turning detection on, {@code false} is default
     */
    boolean pinningDetection() default false;
}
