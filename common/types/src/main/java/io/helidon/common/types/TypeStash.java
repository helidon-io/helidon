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

package io.helidon.common.types;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cache of type names, to avoid duplication of non-generic instances in memory.
 */
final class TypeStash {
    // in a simple registry example, we had over 820 calls to TypeName.create()
    // there were 183 records in this cache - this is a reasonable reduction in memory use
    // (though not as good as for type stash...)
    private static final Map<Class<?>, TypeName> CLASS_TYPE_STASH = new ConcurrentHashMap<>();
    // in a simple registry example, we had over 3000 calls to TypeName.create() without generics
    // there were 201 records in this cache - this is a very good reduction of used memory, as the types are quite often
    // stored for the runtime of the registry
    private static final Map<String, TypeName> TYPE_STASH = new ConcurrentHashMap<>();

    private TypeStash() {
    }

    /**
     * Stash a class. This method can directly cache the result, as it is guaranteed to be generics free.
     *
     * @param clazz class to stash
     * @return type name either cached, or added to the cache
     */
    static TypeName stash(Class<?> clazz) {
        return CLASS_TYPE_STASH.computeIfAbsent(clazz, TypeNameSupport::doCreate);
    }

    /**
     * Stash a class or a generic declaration.
     * If this is a class, it will be cached, if not, a new instance would be created.
     *
     * @param className class name, expected to be fully qualified class name, or a generic declaration
     * @return type name either cached, or a new one depending on the content provided
     */
    static TypeName stash(String className) {
        if (className.indexOf('<') > 0 || className.indexOf('?') > 0 || className.indexOf('.') == -1) {
            // avoid generics
            return TypeNameSupport.doCreate(className);
        }
        return TYPE_STASH.computeIfAbsent(className, TypeNameSupport::doCreate);
    }
}
