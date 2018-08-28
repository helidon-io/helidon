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

package io.helidon.common.reactive.valve;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Delegate filter for {@link Valve}.
 */
class ValveFilter<S, T> implements Valve<S> {

    private final Valve<T> delegate;
    private final Function<BiConsumer<S, Pausable>, BiConsumer<T, Pausable>> filteringFunction;

    ValveFilter(Valve<T> delegate,
                Function<BiConsumer<S, Pausable>, BiConsumer<T, Pausable>> filteringFunction) {
        this.delegate = delegate;
        this.filteringFunction = filteringFunction;
    }

    @Override
    public void pause() {
        delegate.pause();
    }

    @Override
    public void resume() {
        delegate.resume();
    }

    @Override
    public void handle(BiConsumer<S, Pausable> onData, Consumer<Throwable> onError, Runnable onComplete) {
        delegate.handle(filteringFunction.apply(onData), onError, onComplete);
    }
}
