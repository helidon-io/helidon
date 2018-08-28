/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.spi;

import java.util.Optional;

import io.helidon.common.reactive.Flow;

/**
 * Interface used to mark changeable source.
 *
 * @param <T> a type of source
 */
public interface Changeable<T> { //TODO later to be extended just by selected sources

    /**
     * Returns a {@code Flow.Publisher} to which the caller can subscribe in
     * order to receive change notifications.
     * <p>
     * Method {@link Flow.Subscriber#onError(Throwable)} is called in case of error listening on config source data.
     * Method {@link Flow.Subscriber#onComplete()} is never called.
     *
     * @return a publisher of events. Never returns {@code null}
     */
    @Deprecated
    Flow.Publisher<Optional<T>> changes();

}
