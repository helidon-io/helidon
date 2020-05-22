/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Holds a list of {@link java.util.function.Consumer}s to flatten out a call chain of them.
 *
 * @param <T> the element type to accept
 */
class ConsumerChain<T> extends ArrayList<Consumer<? super T>> implements Consumer<T> {

    @Override
    public void accept(T t) {
        for (Consumer<? super T> inner : this) {
            inner.accept(t);
        }
    }

    ConsumerChain<T> combineWith(Consumer<? super T> another) {
        ConsumerChain<T> newChain = new ConsumerChain<>();
        newChain.addAll(this);
        newChain.add(another);
        return newChain;
    }

    @SuppressWarnings("unchecked")
    static <T> Consumer<? super T> combine(Consumer<? super T> current, Consumer<? super T> another) {
        if (current == null) {
            return another;
        }
        if (another == null) {
            return current;
        }
        if (current instanceof ConsumerChain) {
            return ((ConsumerChain<T>) current).combineWith(another);
        }
        ConsumerChain<T> newChain = new ConsumerChain<>();
        newChain.add(current);
        newChain.add(another);
        return newChain;
    }
}
