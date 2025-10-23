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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A cache of type names, to avoid duplication of non-generic instances in memory.
 */
final class TypeStash {
    // in a simple registry example, we had over 820 calls to TypeName.create()
    // there were 183 records in this cache - this is a reasonable reduction in memory use
    // (though not as good as for type stash...)
    private static final Map<Class<?>, TypeName> CLASS_TYPE_STASH = new HashMap<>();
    private static final ReadWriteLock CLASS_TYPE_STASH_LOCK = new ReentrantReadWriteLock();

    // in a simple registry example, we had over 3000 calls to TypeName.create() without generics
    // there were 201 records in this cache - this is a very good reduction of used memory, as the types are quite often
    // stored for the runtime of the registry
    private static final Map<String, TypeName> TYPE_STASH = new HashMap<>();
    private static final ReadWriteLock TYPE_STASH_LOCK = new ReentrantReadWriteLock();

    private TypeStash() {
    }

    /**
     * Stash a class. This method can directly cache the result, as it is guaranteed to be generics free.
     *
     * @param clazz class to stash
     * @return type name either cached, or added to the cache
     */
    static TypeName stash(Class<?> clazz) {
        // using rw lock to make sure we do not have a concurrent modification exception
        // the concurrent modification exception for example on first request, where we may do class initialization of
        // TypeNameSupport, which creates its own types
        CLASS_TYPE_STASH_LOCK.readLock().lock();
        TypeName typeName;
        try {
            typeName = CLASS_TYPE_STASH.get(clazz);
            if (typeName != null) {
                return typeName;
            }
        } finally {
            CLASS_TYPE_STASH_LOCK.readLock().unlock();
        }
        CLASS_TYPE_STASH_LOCK.writeLock().lock();
        try {
            typeName = CLASS_TYPE_STASH.get(clazz);
            if (typeName != null) {
                return typeName;
            }
            typeName = TypeNameSupport.doCreate(clazz);
            CLASS_TYPE_STASH.put(clazz, typeName);
            return typeName;
        } finally {
            CLASS_TYPE_STASH_LOCK.writeLock().unlock();
        }
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
        // using rw lock to make sure we do not have a concurrent modification exception
        // it may happen if we have typed argument that need a creation of another type name
        TYPE_STASH_LOCK.readLock().lock();
        TypeName typeName;
        try {
            typeName = TYPE_STASH.get(className);
            if (typeName != null) {
                return typeName;
            }
        } finally {
            TYPE_STASH_LOCK.readLock().unlock();
        }
        TYPE_STASH_LOCK.writeLock().lock();
        try {
            typeName = TYPE_STASH.get(className);
            if (typeName != null) {
                return typeName;
            }
            typeName = TypeNameSupport.doCreate(className);
            TYPE_STASH.put(className, typeName);
            return typeName;
        } finally {
            TYPE_STASH_LOCK.writeLock().unlock();
        }
    }
}
