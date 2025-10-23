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
package io.helidon.data.codegen.common;

import java.util.LinkedList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

import io.helidon.common.types.TypeInfo;

class TypeInfoSpliterator extends Spliterators.AbstractSpliterator<TypeInfo> {

    private final List<TypeInfo> stack;

    TypeInfoSpliterator(TypeInfo typeInfo) {
        super(estimateSize(typeInfo), Spliterator.SIZED);
        stack = new LinkedList<>();
        stack.add(typeInfo);
    }

    @Override
    public boolean tryAdvance(Consumer<? super TypeInfo> action) {
        if (!stack.isEmpty()) {
            TypeInfo current = stack.removeFirst();
            stack.addAll(current.interfaceTypeInfo());
            action.accept(current);
            return true;
        }
        return false;
    }

    private static long estimateSize(TypeInfo typeInfo) {
        long count = 0;
        List<TypeInfo> stack = new LinkedList<>();
        stack.add(typeInfo);
        while (!stack.isEmpty()) {
            TypeInfo current = stack.removeFirst();
            stack.addAll(current.interfaceTypeInfo());
            count++;
        }
        return count;
    }

}
