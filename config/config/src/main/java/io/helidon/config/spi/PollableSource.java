/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
import java.util.function.Supplier;

/**
 * A source implementing this interface provides support for polling using a {@link io.helidon.config.spi.PollingStrategy}.
 * This is achieved through a stamp passed between the source and the strategy to check for changes.
 * @see io.helidon.config.spi.PollingStrategy
 *
 * @param <S> the type of the stamp used by the source (such as byte[] with a digest of a file)
 */
public interface PollableSource<S> {
    /**
     * This method is invoked to check if this source has changed.
     *
     * @param stamp the stamp of the last loaded content
     * @return {@code true} if the current data of this config source differ from the loaded data, including
     *          cases when the source has disappeared
     */
    boolean isModified(S stamp);

    /**
     * If a polling strategy is configured with this source, return it.
     * The source implementation does not need to handle polling strategy.
     *
     * @return polling strategy if one is configured on this source
     */
    Optional<PollingStrategy> pollingStrategy();

    /**
     * A builder for pollable source.
     *
     * @param <T> type of the builder, used when extending this builder
     * @see io.helidon.config.AbstractConfigSourceBuilder
     * @see io.helidon.config.AbstractConfigSource
     */
    interface Builder<T extends Builder<T>> {
        T pollingStrategy(PollingStrategy pollingStrategy);

        default T pollingStrategy(Supplier<? extends PollingStrategy> pollingStrategy) {
            return pollingStrategy(pollingStrategy.get());
        }
    }
}
