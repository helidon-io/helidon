/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

/**
 * {@link MonoCollector} implementation that collects items in a {@link List}.
 * @param <U> collected item type
 */
final class MonoListCollector<U> extends MonoCollector<U, List<U>> {

    private final ArrayList<U> list;

    MonoListCollector() {
        this.list = new ArrayList<>();
    }

    @Override
    public void collect(U item) {
        list.add(item);
    }

    @Override
    public List<U> value() {
        return list;
    }
}
