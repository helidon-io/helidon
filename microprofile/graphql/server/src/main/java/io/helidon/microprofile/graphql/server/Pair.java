/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import java.util.Objects;

public class Pair<A, B> {
    private final A valueA;
    private final B valueB;

    public Pair(A valueA, B valueB) {
        this.valueA = valueA;
        this.valueB = valueB;
    }

    public A getValueA() {
        return valueA;
    }

    public B getValueB() {
        return valueB;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(valueA, pair.valueA) &&
                Objects.equals(valueB, pair.valueB);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valueA, valueB);
    }
}
