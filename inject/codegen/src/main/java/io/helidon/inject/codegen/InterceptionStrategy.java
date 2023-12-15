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

package io.helidon.inject.codegen;

/**
 * Possible strategies to interception.
 * Whether interception is supported, and this is honored depends on implementation.
 * <p>
 * The strategy is (in Helidon inject) only honored at compilation time. At runtime, it can only be enabled or disabled.
 */
public enum InterceptionStrategy {
    /**
     * No annotations will qualify in triggering interceptor creation (interception is disabled).
     */
    NONE,
    /**
     * Meta-annotation based. Only annotations annotated with {@code Interception.Trigger} will
     * qualify.
     * This is the default strategy.
     */
    EXPLICIT,

    /**
     * All annotations marked as {@link java.lang.annotation.RetentionPolicy#RUNTIME} will qualify.
     * Also includes all usages of {@link #EXPLICIT}.
     */
    ALL_RUNTIME,
    /**
     * All annotations marked as {@link java.lang.annotation.RetentionPolicy#RUNTIME} and
     * {@link java.lang.annotation.RetentionPolicy#CLASS} will qualify.
     * Also includes all usages of {@link #EXPLICIT}.
     */
    ALL_RETAINED
}
