/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.Api;
import io.helidon.common.GenericType;

/**
 * A mapping failure between source and target types.
 * <p>
 * This base exception covers both lookup failures, when no mapper is registered, and execution failures reported by a
 * mapper that was found. Use {@link #sourceType()}, {@link #targetType()}, and {@link #detail()} to inspect the
 * failed mapping without parsing {@link #getMessage()}. Some APIs may report missing-mapper cases using the more
 * specific {@link MapperNotFoundException}.
 */
@Api.Stable
public class MapperException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final GenericType<?> sourceType;
    private final GenericType<?> targetType;
    private final String detail;

    /**
     * Failed with no underlying exception.
     *
     * @param source type of the source
     * @param target type of the target
     * @param detail descriptive message of what failed
     */
    public MapperException(GenericType<?> source, GenericType<?> target, String detail) {
        super(createMessage(source, target, detail));
        this.sourceType = source;
        this.targetType = target;
        this.detail = detail;
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
        super(createMessage(source, target, detail), cause);
        this.sourceType = source;
        this.targetType = target;
        this.detail = detail;
    }

    /**
     * Type of the source involved in the failed mapping attempt.
     *
     * @return source type
     */
    public GenericType<?> sourceType() {
        return sourceType;
    }

    /**
     * Type of the requested target involved in the failed mapping attempt.
     *
     * @return target type
     */
    public GenericType<?> targetType() {
        return targetType;
    }

    /**
     * Detail describing the mapping failure.
     * <p>
     * This is the unformatted detail appended to the standard exception message.
     *
     * @return failure detail
     */
    public String detail() {
        return detail;
    }

    private static String createMessage(GenericType<?> source, GenericType<?> target, String detail) {
        return "Failed to map " + source.getTypeName() + " to " + target.getTypeName() + ": " + detail;
    }
}
