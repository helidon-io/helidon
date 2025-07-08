/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.Collection;
import java.util.List;
import java.util.stream.StreamSupport;

class HeaderValueIterable extends HeaderValueBase {
    private final Iterable<String> originalValues;
    private int valueCount;

    HeaderValueIterable(HeaderName name, boolean changing, boolean sensitive, Iterable<String> values) {
        super(name, changing, sensitive, values.iterator().next());

        this.originalValues = values;
    }

    private int getIterableSize() {
        int iterableSize = 0;
        if (originalValues instanceof Collection) {
            iterableSize = ((Collection<?>) originalValues).size();
        } else {
            // non-Collection type does not have size() so we need to count the elements
            for (Object i : originalValues) {
                iterableSize++;
            }
        }
        return iterableSize;
    }

    @Override
    public List<String> allValues() {
        return StreamSupport.stream(originalValues.spliterator(), false).toList();
    }

    @Override
    public int valueCount() {
        if (valueCount == 0) {
            valueCount = getIterableSize();
        }
        return valueCount;
    }
}
