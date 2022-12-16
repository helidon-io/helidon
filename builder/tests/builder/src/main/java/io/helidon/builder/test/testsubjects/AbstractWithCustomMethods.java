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

package io.helidon.builder.test.testsubjects;

import java.util.Optional;

import io.helidon.builder.Builder;

/**
 * Usage of an abstract class target, as well as overriding {@link #toString()}, {@link #hashCode()}, and {@link #equals(Object)}.
 */
@Builder(interceptor = GeneralInterceptor.class)
public abstract class AbstractWithCustomMethods {

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    public abstract String name();

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    public abstract boolean isStatic();

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    public abstract boolean isClass();

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    public abstract boolean isInterface();

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    public abstract boolean isAbstract();

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    public abstract boolean isFinal();

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    public abstract boolean isPublic();

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    public abstract boolean isProtected();

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    public abstract Optional<String> getAbstract();

    /**
     * Used for testing.
     *
     * @return ignored, here for testing purposes only
     */
    public abstract boolean isPrivate();

    @Override
    public String toString() {
        return name();
    }

    @Override
    public int hashCode() {
        return 1;
    }

    @Override
    public boolean equals(Object another) {
        return true;
    }

}
