/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.common.context.http;

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.http.HeaderName;

/**
 * Configuration of a single propagation record, a mapping of a header name to its context classifier, with optional
 * default value(s), and definition whether it is a single value, or an array.
 */
@Prototype.Configured
@Prototype.CustomMethods(ContextRecordConfigSupport.RecordCustomMethods.class)
@Prototype.Blueprint
interface ContextRecordConfigBlueprint {
    /**
     * Name of the header to use when sending the context value over the network.
     *
     * @return header name
     */
    @Option.Configured
    HeaderName header();

    /**
     * String classifier of the value that will be used with {@link io.helidon.common.context.Context#get(Object, Class)}.
     *
     * @return classifier to use, defaults to header name
     */
    @Option.Configured
    Optional<String> classifier();

    /**
     * Default value to send if not configured in context.
     *
     * @return default value, used for non-array records, or when only a single value is desired as a default for array
     */
    @Option.Configured
    Optional<String> defaultValue();

    /**
     * Default values to send if not configured in context.
     * In case default values is an empty array, it will not be sent over the network if not present in context.
     *
     * @return default values, used for array records; if this record is not an array, only the first value will be used
     */
    @Option.Configured
    @Option.Singular
    List<String> defaultValues();

    /**
     * Whether to treat the option as an array of strings.
     * This would be read from the context as an array.
     *
     * @return whether the record is an array
     */
    @Option.Configured
    boolean array();
}
