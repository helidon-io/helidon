/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.Flow;

/**
 * Base abstract implementation of {@link OverrideSource}, suitable for concrete
 * implementations to extend.
 *
 * @param <S> a type of data stamp
 * @see Builder
 */
public abstract class AbstractOverrideSource<S> extends AbstractSource<OverrideSource.OverrideData, S> implements OverrideSource {

    /**
     * Initializes config source from builder.
     *
     * @param builder builder to be initialized from
     */
    protected AbstractOverrideSource(Builder<?, ?> builder) {
        super(builder);
    }

    @Override
    public final Flow.Publisher<Optional<OverrideData>> changes() {
        return changesPublisher();
    }

    /**
     * A common {@link OverrideSource} builder ready to be extended by builder implementation related to {@link OverrideSource}
     * extensions.
     * <p>
     *
     * @param <B> type of Builder implementation
     * @param <T> type of key source attributes (target) used to construct polling strategy from
     */
    public abstract static class Builder<B extends Builder<B, T>, T>
            extends AbstractSource.Builder<B, T, OverrideSource>
            implements io.helidon.common.Builder<OverrideSource> {

        private volatile OverrideSource overrideSource;

        /**
         * Initialize builder.
         *
         * @param targetType target type
         */
        protected Builder(Class<T> targetType) {
            super(targetType);
        }

        @Override
        public OverrideSource get() {
            if (overrideSource == null) {
                overrideSource = build();
            }
            return overrideSource;
        }
    }

}
