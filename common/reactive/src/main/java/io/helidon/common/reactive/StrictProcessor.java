/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package io.helidon.common.reactive;

/**
 * Reactive processor with ability to conform reactive streams rule 1.3.
 * Rule 1.3: {@code onSubscribe}, {@code onNext}, {@code onError} and {@code onComplete}
 * signaled to a Subscriber MUST be signaled serially.
 *
 * @see <a href="https://github.com/reactive-streams/reactive-streams-jvm#1.3">Reactive streams rule 1.3</a>
 */
public interface StrictProcessor {

    /**
     * Default value for strict mode is FALSE,
     * can be overridden by system property {@code helidon.common.reactive.strict.mode}.
     */
    boolean DEFAULT_STRICT_MODE = Boolean.parseBoolean(
            System.getProperty("helidon.common.reactive.strict.mode", Boolean.FALSE.toString()));

    /**
     * Sets the flag if the processor should strictly force rule 1.3 when calling its subscriber.
     *
     * @param strictMode true for strict mode
     * @return this
     */
    StrictProcessor strictMode(boolean strictMode);
}
