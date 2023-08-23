/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.providers.micrometer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

class Util {

    private Util() {
    }

    static double[] doubleArray(Iterable<Double> iter) {
        List<Double> values = new ArrayList<>();
        iter.forEach(values::add);
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    static <T> List<T> list(Iterable<T> iterable) {
        List<T> result = new ArrayList<>();
        iterable.forEach(result::add);
        return result;
    }

    static Iterable<Double> iterable(double[] items) {
        return items == null
                ? Set.of()
                : Arrays.stream(items)
                        .boxed()
                        .toList();
    }
}
