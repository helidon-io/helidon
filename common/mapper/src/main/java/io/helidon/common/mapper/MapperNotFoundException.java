/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.common.mapper;

import io.helidon.common.GenericType;

/**
 * A {@link MapperException} thrown when no mapper exists for the requested source and target types.
 */
public class MapperNotFoundException extends MapperException {

    /**
     * Create a new exception with no underlying cause.
     *
     * @param source type of the source
     * @param target type of the target
     * @param detail descriptive message of what failed
     */
    public MapperNotFoundException(GenericType<?> source, GenericType<?> target, String detail) {
        super(source, target, detail);
    }

    /**
     * Create a new exception with an underlying cause.
     *
     * @param source type of the source
     * @param target type of the target
     * @param detail descriptive message of what failed
     * @param cause cause of this exception
     */
    public MapperNotFoundException(GenericType<?> source, GenericType<?> target, String detail, Throwable cause) {
        super(source, target, detail, cause);
    }
}
