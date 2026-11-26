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

package io.helidon.json.smile;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.common.Size;

/**
 * Configuration of Smile encoding and decoding.
 * Generators use the encoding options to write the header. Parsers obtain the encoding features from the input header.
 */
@Api.Preview
@Prototype.Blueprint(decorator = SmileConfigSupport.class)
@Prototype.Configured
@Prototype.IncludeDefaultMethods
interface SmileConfigBlueprint {

    /**
     * Maximum capacity to which a marked Smile stream input buffer may grow (unlimited when absent).
     * Existing buffer capacity remains usable. Zero prevents marked growth.
     * The parser fixes this limit at construction. The supported range is 0 through {@link Integer#MAX_VALUE} bytes.
     *
     * @return optional replay buffer size limit
     */
    @Option.Configured
    default Optional<Size> maxReplaySize() {
        return Optional.empty();
    }

    /**
     * Whether to enable shared key string references.
     *
     * @return {@code true} to enable shared key string references, defaults to {@code true}
     */
    @Option.DefaultBoolean(true)
    @Option.Configured
    boolean sharedKeyStrings();

    /**
     * Whether to enable shared value string references.
     *
     * @return {@code true} to enable shared value string references
     */
    @Option.Configured
    boolean sharedValueStrings();

    /**
     * Whether to allow raw binary values in Smile content.
     *
     * @return {@code true} to allow raw binary values
     */
    @Option.Configured
    boolean rawBinaryEnabled();

    /**
     * Whether to emit the optional Smile end marker when closing a generator.
     *
     * @return {@code true} to emit the end marker
     */
    @Option.Configured
    boolean emitEndMark();
}
