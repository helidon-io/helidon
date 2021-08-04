/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.common.reactive;

import java.util.ArrayList;
import java.util.function.BiConsumer;

class BiConsumerChain<T, S>
        extends ArrayList<BiConsumer<? super T, ? super S>>
        implements BiConsumer<T, S> {

    @Override
    public void accept(T t, S s) {
        for (BiConsumer<? super T, ? super S> inner : this) {
            inner.accept(t, s);
        }
    }

    BiConsumerChain<T, S> combineWith(BiConsumer<? super T, ? super S> another) {
        BiConsumerChain<T, S> newChain = new BiConsumerChain<>();
        newChain.addAll(this);
        newChain.add(another);
        return newChain;
    }

    static <T, S> BiConsumer<T, S> combine(
            BiConsumer<T, S> current,
            BiConsumer<T, S> another) {
        if (current == null) {
            return another;
        }
        if (another == null) {
            return current;
        }
        if (current instanceof BiConsumerChain) {
            return ((BiConsumerChain<T, S>) current).combineWith(another);
        }
        BiConsumerChain<T, S> newChain = new BiConsumerChain<>();
        if (current instanceof BiConsumerChain) {
            newChain.addAll((BiConsumerChain<T, S>) current);
        } else {
            newChain.add(current);
        }

        if (another instanceof BiConsumerChain) {
            newChain.addAll((BiConsumerChain<T, S>) another);
        } else {
            newChain.add(another);
        }
        return newChain;
    }
}
