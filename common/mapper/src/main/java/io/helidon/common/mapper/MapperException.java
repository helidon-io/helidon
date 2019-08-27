/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 * An exception that is thrown when mapping failed to map source to target.
 * This may be either a problem that the mapper was not found (it is not registered) or that the mapping itself failed.
 */
public class MapperException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Failed with no underlying exception.
     *
     * @param source type of the source
     * @param target type of the target
     * @param detail descriptive message of what failed
     */
    public MapperException(GenericType<?> source, GenericType<?> target, String detail) {
        super("Failed to map " + source.getTypeName() + " to " + target.getTypeName() + ": " + detail);
    }

    /**
     * Failed with an underlying exception.
     *
     * @param source type of the source
     * @param target type of the target
     * @param detail descriptive message of what failed
     * @param cause cause of this exception
     */
    public MapperException(GenericType<?> source, GenericType<?> target, String detail, Throwable cause) {
        super("Failed to map " + source.getTypeName() + " to " + target.getTypeName() + ": " + detail, cause);
    }
}
