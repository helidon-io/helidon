/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.allocator;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * An allocator of thread-local objects.
 */
@Singleton
public class Allocator {


    /*
     * Static fields.
     */


    private static ThreadLocal<Map<Allocator, Map<AllocationId, Allocation<?>>>> tl = ThreadLocal.withInitial(HashMap::new);


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link Allocator}.
     */
    @Inject
    public Allocator() {
        super();
    }


    /*
     * Instance methods.
     */


    /**
     * Retrieves the current thread's value for the supplied {@link Class} and qualifier annotations.
     *
     * @param <T> the type of the object
     *
     * @param s a {@link Supplier} to use to create or otherwise get the object if it is not already stored
     *
     * @param type the type used to select a contextual reference; must not be {@code null}
     *
     * @param qualifiers qualifier annotations used to select a contextual reference; must not be {@code null}
     *
     * @return the allocated object, or {@code null} if the supplied {@link Supplier} returns {@code null} from its
     * {@link Supplier#get()} method implementation
     *
     * @exception NullPointerException if {@code type} or {@code qualifiers} is {@code null}
     */
    public final <T> T allocate(Supplier<? extends T> s, Class<T> type, Set<Annotation> qualifiers) {
        return this.allocate(s, Set.of(type), qualifiers);
    }

    /**
     * Retrieves the current thread's value for the supplied {@link Class} and qualifier annotations.
     *
     * @param <T> the type of the object
     *
     * @param s a {@link Supplier} to use to create or otherwise get the object if it is not already stored
     *
     * @param type the type used to select a contextual reference; must not be {@code null}
     *
     * @param qualifiers qualifier annotations used to select a contextual reference; must not be {@code null}
     *
     * @return the allocated object, or {@code null} if the supplied {@link Supplier} returns {@code null} from its
     * {@link Supplier#get()} method implementation
     *
     * @exception NullPointerException if {@code type} or {@code qualifiers} is {@code null}
     */
    public final <T> T allocate(Supplier<? extends T> s, Class<T> type, Annotation... qualifiers) {
        return this.allocate(s, Set.of(type), Set.copyOf(Arrays.asList(qualifiers)));
    }

    /**
     * Retrieves the current thread's value for the supplied {@link TypeLiteral} and qualifier annotations.
     *
     * @param <T> the type of the object
     *
     * @param s a {@link Supplier} to use to create or otherwise get the object if it is not already stored
     *
     * @param type the type used to select a contextual reference; must not be {@code null}
     *
     * @param qualifiers qualifier annotations used to select a contextual reference; must not be {@code null}
     *
     * @return the allocated object, or {@code null} if the supplied {@link Supplier} returns {@code null} from its
     * {@link Supplier#get()} method implementation
     *
     * @exception NullPointerException if {@code type} or {@code qualifiers} is {@code null}
     */
    public final <T> T allocate(Supplier<? extends T> s, TypeLiteral<T> type, Set<Annotation> qualifiers) {
        return this.allocate(s, Set.of(type.getType()), qualifiers);
    }

    /**
     * Retrieves the current thread's value for the supplied {@link TypeLiteral} and qualifier annotations.
     *
     * @param <T> the type of the object
     *
     * @param s a {@link Supplier} to use to create or otherwise get the object if it is not already stored
     *
     * @param type the type used to select a contextual reference; must not be {@code null}
     *
     * @param qualifiers qualifier annotations used to select a contextual reference; must not be {@code null}
     *
     * @return the allocated object, or {@code null} if the supplied {@link Supplier} returns {@code null} from its
     * {@link Supplier#get()} method implementation
     *
     * @exception NullPointerException if {@code type} or {@code qualifiers} is {@code null}
     */
    public final <T> T allocate(Supplier<? extends T> s, TypeLiteral<T> type, Annotation... qualifiers) {
        return this.allocate(s, Set.of(type.getType()), Set.copyOf(Arrays.asList(qualifiers)));
    }

    /**
     * Retrieves the current thread's value for the supplied {@link Set} of {@link Type}s and qualifier annotations.
     *
     * @param <T> the type of the object
     *
     * @param s a {@link Supplier} to use to create or otherwise get the object if it is not already stored; must not be
     * {@code null}
     *
     * @param types the types of the object; must not be {@code null}
     *
     * @param qualifiers qualifier annotations used to select a contextual reference; must not be {@code null}
     *
     * @return the allocated object, or {@code null} if the supplied {@link Supplier} returns {@code null} from its
     * {@link Supplier#get()} method implementation
     *
     * @exception NullPointerException if {@code types} or {@code qualifiers} is {@code null}
     */
    public final <T> T allocate(Supplier<? extends T> s, Set<Type> types, Annotation... qualifiers) {
        return this.allocate(s, types, Set.copyOf(Arrays.asList(qualifiers)));
    }

    /**
     * Retrieves the current thread's value for the supplied {@link Set} of {@link Type}s and qualifier annotations.
     *
     * @param <T> the type of the object
     *
     * @param s a {@link Supplier} to use to create or otherwise get the object if it is not already stored; must not be
     * {@code null}
     *
     * @param types the types of the object; must not be {@code null}
     *
     * @param qualifiers qualifier annotations used to select a contextual reference; must not be {@code null}
     *
     * @return the allocated object, or {@code null} if the supplied {@link Supplier} returns {@code null} from its
     * {@link Supplier#get()} method implementation
     *
     * @exception NullPointerException if {@code types} or {@code qualifiers} is {@code null}
     */
    public <T> T allocate(Supplier<? extends T> s, Set<Type> types, Set<Annotation> qualifiers) {
        Map<AllocationId, Allocation<?>> map2 = tl.get().computeIfAbsent(this, k -> new HashMap<>());
        @SuppressWarnings("unchecked")
        Allocation<T> allocation =
            (Allocation<T>) map2.computeIfAbsent(new AllocationId(Set.copyOf(types), Set.copyOf(qualifiers)),
                                                 k -> new Allocation<>(s.get()));
        ++allocation.rc;
        return allocation.object;
    }

    /**
     * Releases the current thread's value for the supplied {@link Class} and qualifier annotations if the number of
     * {@linkplain #allocate(Supplier, Class, Annotation...) allocations} is zero.
     *
     * @param <T> the type of the object
     *
     * @param c a {@link Consumer} to use to destroy or otherwise release the current thread's value if the number of
     * {@linkplain #allocate(Supplier, Class, Annotation...) allocations} is zero
     *
     * @param type the type used to select a contextual reference; must not be {@code null}
     *
     * @param qualifiers qualifier annotations used to select a contextual reference; must not be {@code null}
     *
     * @exception NullPointerException if {@code type} or {@code qualifiers} is {@code null}
     */
    @SuppressWarnings("unchecked")
    public final <T> void release(Consumer<? super T> c, Class<T> type, Set<Annotation> qualifiers) {
        this.release(c, Set.of(type), qualifiers);
    }

    /**
     * Releases the current thread's value for the supplied {@link Class} and qualifier annotations if the number of
     * {@linkplain #allocate(Supplier, Class, Annotation...) allocations} is zero.
     *
     * @param <T> the type of the object
     *
     * @param c a {@link Consumer} to use to destroy or otherwise release the current thread's value if the number of
     * {@linkplain #allocate(Supplier, Class, Annotation...) allocations} is zero
     *
     * @param type the type used to select a contextual reference; must not be {@code null}
     *
     * @param qualifiers qualifier annotations used to select a contextual reference; must not be {@code null}
     *
     * @exception NullPointerException if {@code type} or {@code qualifiers} is {@code null}
     */
    @SuppressWarnings("unchecked")
    public final <T> void release(Consumer<? super T> c, Class<T> type, Annotation... qualifiers) {
        this.release(c, Set.of(type), Set.copyOf(Arrays.asList(qualifiers)));
    }

    /**
     * Releases the current thread's value for the supplied {@link Class} and qualifier annotations if the number of
     * {@linkplain #allocate(Supplier, Class, Annotation...) allocations} is zero.
     *
     * @param <T> the type of the object
     *
     * @param c a {@link Consumer} to use to destroy or otherwise release the current thread's value if the number of
     * {@linkplain #allocate(Supplier, Class, Annotation...) allocations} is zero
     *
     * @param type the type used to select a contextual reference; must not be {@code null}
     *
     * @param qualifiers qualifier annotations used to select a contextual reference; must not be {@code null}
     *
     * @exception NullPointerException if {@code type} or {@code qualifiers} is {@code null}
     */
    public final <T> void release(Consumer<? super T> c, TypeLiteral<T> type, Set<Annotation> qualifiers) {
        this.release(c, Set.of(type.getType()), qualifiers);
    }

    /**
     * Releases the current thread's value for the supplied {@link Class} and qualifier annotations if the number of
     * {@linkplain #allocate(Supplier, Class, Annotation...) allocations} is zero.
     *
     * @param <T> the type of the object
     *
     * @param c a {@link Consumer} to use to destroy or otherwise release the current thread's value if the number of
     * {@linkplain #allocate(Supplier, Class, Annotation...) allocations} is zero
     *
     * @param type the type used to select a contextual reference; must not be {@code null}
     *
     * @param qualifiers qualifier annotations used to select a contextual reference; must not be {@code null}
     *
     * @exception NullPointerException if {@code type} or {@code qualifiers} is {@code null}
     */
    public final <T> void release(Consumer<? super T> c, TypeLiteral<T> type, Annotation... qualifiers) {
        this.release(c, Set.of(type.getType()), Set.copyOf(Arrays.asList(qualifiers)));
    }

    /**
     * Releases the current thread's value for the supplied {@link Set} of {@link Type}s and qualifier annotations if
     * the number of {@linkplain #allocate(Supplier, Set, Annotation...) allocations} is zero.
     *
     * @param <T> the type of the object
     *
     * @param c a {@link Consumer} to use to destroy or otherwise release the current thread's value if the number of
     * {@linkplain #allocate(Supplier, Class, Annotation...) allocations} is zero
     *
     * @param types the types of the object; must not be {@code null}
     *
     * @param qualifiers qualifier annotations used to select a contextual reference; must not be {@code null}
     *
     * @exception NullPointerException if {@code types} or {@code qualifiers} is {@code null}
     */
    public final <T> void release(Consumer<? super T> c, Set<Type> types, Annotation... qualifiers) {
        this.release(c, types, Set.copyOf(Arrays.asList(qualifiers)));
    }

    /**
     * Releases the current thread's value for the supplied {@link Set} of {@link Type}s and qualifier annotations if
     * the number of {@linkplain #allocate(Supplier, Set, Set) allocations} is zero.
     *
     * @param <T> the type of the object
     *
     * @param c a {@link Consumer} to use to destroy or otherwise release the current thread's value if the number of
     * {@linkplain #allocate(Supplier, Class, Annotation...) allocations} is zero
     *
     * @param types the types of the object; must not be {@code null}
     *
     * @param qualifiers qualifier annotations used to select a contextual reference; must not be {@code null}
     *
     * @exception NullPointerException if {@code types} or {@code qualifiers} is {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> void release(Consumer<? super T> c, Set<Type> types, Set<Annotation> qualifiers) {
        Map<AllocationId, Allocation<?>> map2 = tl.get().get(this);
        if (map2 != null) {
            AllocationId id = new AllocationId(Set.copyOf(types), Set.copyOf(qualifiers));
            Allocation<?> allocation = map2.get(id);
            if (allocation != null && --allocation.rc == 0) {
                map2.remove(id);
                c.accept((T) allocation.object);
            }
        }
    }


    /*
     * Inner and nested classes.
     */


    private static final class Allocation<T> {

        private final T object;

        private int rc;

        private Allocation(T object) {
            super();
            this.object = object;
        }

    }

    private static final record AllocationId(Set<Type> types, Set<Annotation> qualifiers) {}

}
