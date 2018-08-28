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

/**
 * Represents a Valve which is empty.
 * <p>
 * For the performance sake the Valve accepts unlimited number of handlers.
 * Each complete handler is called as soon as registered.
 */
class EmptyValve<T> implements Valve<T> {

    @Override
    public void handle(BiConsumer<T, Pausable> onData, Consumer<Throwable> onError, Runnable onComplete) {
        if (onComplete != null) {
            onComplete.run();
        }
    }

    @Override
    public void pause() {
        // No-op
    }

    @Override
    public void resume() {
        // No-op
    }
}
