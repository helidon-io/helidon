/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.builder.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This class holds all types related to runtime types, configured from prototypes.
 */
public final class RuntimeType {
    private RuntimeType() {
    }

    /**
     * This type is created from a specific prototype.
     *
     * @param <T> type of the prototype
     */
    public interface Api<T extends Prototype.Api> {
        /**
         * The prototype as it was received when creating this runtime object instance.
         *
         * @return prototype object used to create this instance
         */
        T prototype();
    }

    /**
     * This annotation is no longer used.
     *
     * @deprecated this is an unnecessary duplication of what is required on the blueprint, use only blueprint
     * {@link io.helidon.builder.api.Prototype.Factory} instead.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @Deprecated(forRemoval = true, since = "4.4.0")
    public @interface PrototypedBy {
        /**
         * Type of the prototype.
         *
         * @return prototype class
         */
        Class<? extends Prototype.Api> value();
    }
}
