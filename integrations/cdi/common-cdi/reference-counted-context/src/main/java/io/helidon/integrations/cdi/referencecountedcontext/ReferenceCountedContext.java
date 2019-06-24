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
package io.helidon.integrations.cdi.referencecountedcontext;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.enterprise.context.spi.AlterableContext;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;

/**
 * An {@link AlterableContext} that destroys its objects when their
 * reference count drops to zero or less than zero.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Instances of this class are safe for concurrent use by multiple
 * threads.</p>
 */
final class ReferenceCountedContext implements AlterableContext {


    /*
     * Static fields.
     */


    /**
     * A {@link ThreadLocal} containing a {@link Map} whose keys are
     * instances of this class, and whose values are {@link Map}s
     * indexing {@link Instance}s by {@link Contextual} instances.
     */
    private static final ThreadLocal<Map<ReferenceCountedContext, Map<Contextual<?>, Instance<?>>>> ALL_INSTANCES =
        ThreadLocal.withInitial(() -> new HashMap<>());


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link ReferenceCountedContext}.
     */
    ReferenceCountedContext() {
        super();
        ALL_INSTANCES.get().put(this, new HashMap<>());
    }


    /*
     * Instance methods.
     */


    /**
     * Decrements the reference count of the contextual instance, if
     * any, associated with the combination of the current thread and
     * the supplied {@link Contextual}, destroying the instance if the
     * reference count becomes zero, and returns the resulting reference
     * count.
     *
     * @param c the {@link Contextual} whose instance's reference count
     * should be decremented; may be {@code null} in which case no
     * action will be taken and {@code 0} will be returned
     *
     * @return the resulting reference count
     */
    public int decrementReferenceCount(final Contextual<?> c) {
        final int returnValue;
        if (c == null) {
            returnValue = 0;
        } else {
            final Map<?, ? extends Instance<?>> instances = ALL_INSTANCES.get().get(this);
            assert instances != null;
            final Instance<?> instance = instances.get(c);
            if (instance == null) {
                returnValue = 0;
            } else {
                returnValue = instance.decrementReferenceCount();
                if (returnValue <= 0) {
                    instances.remove(c);
                }
            }
        }
        return returnValue;
    }

    /**
     * Calls the {@link #decrementReferenceCount(Contextual)} method,
     * and, if its return value is less than or equal to {@code 0},
     * destroys the supplied {@link Contextual} such that a subsequent
     * invocation of {@link #get(Contextual, CreationalContext)} will
     * return a newly created contextual instance.
     *
     * @param bean the {@link Contextual} to destroy; may be {@code
     * null} in which case no action will be taken
     *
     * @see #get(Contextual, CreationalContext)
     */
    @Override
    public void destroy(final Contextual<?> bean) {
        if (bean != null && this.decrementReferenceCount(bean) <= 0) {
            ALL_INSTANCES.get().get(this).remove(bean);
        }
    }

    /**
     * Returns the contextual instance associated with the current
     * thread and the supplied {@link Contextual}, or {@code null} if no
     * such contextual instance exists.
     *
     * @param bean the {@link Contextual} in question; may be {@code
     * null} in which case {@code null} wil be returned
     *
     * @return the contextual instance associated with the current
     * thread and the supplied {@link Contextual}, or {@code null}
     */
    @Override
    public <T> T get(final Contextual<T> bean) {
        return this.get(bean, null, false);
    }

    /**
     * Returns the contextual instance associated with the current
     * thread and the supplied {@link Contextual}, {@linkplain
     * Contextual#create(CreationalContext) creating} it if necessary.
     *
     * @param bean the {@link Contextual} in question; may be {@code
     * null} in which case {@code null} wil be returned
     *
     * @param cc a {@link CreationalContext} that will hold dependent
     * instances; may be {@code null}
     *
     * @return the contextual instance associated with the current
     * thread and the supplied {@link Contextual}; may strictly speaking
     * be {@code null} but normally is not
     */
    @Override
    public <T> T get(final Contextual<T> bean, final CreationalContext<T> cc) {
        return this.get(bean, cc, true);
    }

    /**
     * Returns the contextual instance associated with the current
     * thread and the supplied {@link Contextual}, {@linkplain
     * Contextual#create(CreationalContext) creating} it if the {@code
     * maybeCreate} parameter is {@code true}.
     *
     * @param <T> the type of the contextual instance to get
     *
     * @param bean the {@link Contextual} in question; may be {@code
     * null} in which case {@code null} wil be returned
     *
     * @param cc a {@link CreationalContext} that will hold dependent
     * instances; may be {@code null}
     *
     * @param maybeCreate whether or not to create a new contextual
     * instance if required
     *
     * @return the possibly newly-created contextual instance associated
     * with the current thread and the supplied {@link Contextual}, or
     * {@code null}
     */
    private <T> T get(final Contextual<T> bean, final CreationalContext<T> cc, final boolean maybeCreate) {
        final T returnValue;
        if (bean == null) {
            returnValue = null;
        } else {
            final Map<Contextual<?>, Instance<?>> instances = ALL_INSTANCES.get().get(this);
            assert instances != null;
            @SuppressWarnings("unchecked")
                final Instance<T> temp = (Instance<T>) instances.get(bean);
            Instance<T> instance = temp;
            if (instance == null) {
                if (maybeCreate) {
                    instance = new Instance<T>(bean, cc);
                    instances.put(bean, instance);
                    returnValue = instance.get();
                } else {
                    returnValue = null;
                }
            } else {
                returnValue = instance.get();
            }
        }
        return returnValue;
    }

    /**
     * Returns {@link ReferenceCounted ReferenceCounted.class} when
     * invoked.
     *
     * @return {@link ReferenceCounted ReferenceCounted.class} in all
     * cases
     */
    @Override
    public Class<? extends Annotation> getScope() {
        return ReferenceCounted.class;
    }

    /**
     * Returns {@code true} when invoked.
     *
     * @return {@code true} in all cases
     */
    @Override
    public boolean isActive() {
        return true;
    }


    /*
     * Inner and nested classes.
     */


    /**
     * A coupling of an object (a contextual instance), the {@link
     * CreationalContext} in effect when it was created, the {@link
     * Contextual} that {@linkplain Contextual#create(CreationalContext)
     * created} it, and a reference count.
     *
     * <h2>Thread Safety</h2>
     *
     * <p>Instances of this class are <strong>not</strong> safe for
     * concurrent use by multiple threads.</p>
     *
     * @see CreationalContext
     *
     * @see Contextual
     */
    private static final class Instance<T> {


        /*
         * Instance fields.
         */


        /**
         * The contextual instance that was created and that forms the
         * core of this {@link Instance}.
         *
         * <p>This field may be {@code null}.</p>
         */
        private T object;

        /**
         * The reference count of this {@link Instance}, basically
         * equivalent to the number of times that {@link
         * ReferenceCountedContext#get(Contextual, CreationalContext)} and
         * {@link ReferenceCountedContext#get(Contextual)} have been
         * called.
         *
         * <p>If this field's value is less than zero, then {@link #get()}
         * invocations will throw {@link IllegalStateException}.</p>
         *
         * @see #decrementReferenceCount()
         *
         * @see #get()
         */
        private int referenceCount;

        /**
         * The {@link CreationalContext} supplied to this {@link Instance}
         * at construction time.
         *
         * <p>This field may be {@code null}.</p>
         */
        private final CreationalContext<T> creationalContext;

        /**
         * The {@link Contextual} that created the object this {@link
         * Instance} wraps.
         *
         * <p>This field will never be {@code null}.</p>
         */
        private final Contextual<T> bean;


        /*
         * Constructors.
         */


        /**
         * Creates a new {@link Instance}.
         *
         * @param bean the {@link Contextual} that creates and destroys
         * contextual instances; must not be {@code null}
         *
         * @param creationalContext the {@link CreationalContext} that
         * will track dependent instances of the contextual instance; may
         * be {@code null}
         *
         * @exception NullPointerException if {@code bean} is {@code null}
         *
         * @see Contextual
         *
         * @see CreationalContext
         *
         * @see Contextual#create(CreationalContext)
         */
        private Instance(final Contextual<T> bean, final CreationalContext<T> creationalContext) {
            super();
            this.bean = Objects.requireNonNull(bean);
            this.creationalContext = creationalContext;
            this.object = this.bean.create(creationalContext);
        }


        /*
         * Instance methods.
         */


        /**
         * Returns the contextual instance this {@link Instance} wraps and
         * increments its reference count.
         *
         * <p>This method may return {@code null} if the contextual
         * instance this {@link Instance} was created with was {@code
         * null} at construction time.</p>
         *
         * @return the contextual instance this {@link Instance} wraps, or
         * {@code null}
         *
         * @exception IllegalStateException if the reference count
         * internally is less than zero, such as when an invocation of
         * {@link #decrementReferenceCount()} has resulted in destruction
         */
        private T get() {
            if (this.referenceCount < 0) {
                throw new IllegalStateException("this.referenceCount < 0: " + this.referenceCount);
            } else if (this.referenceCount < Integer.MAX_VALUE) {
                ++this.referenceCount;
            }
            return this.object;
        }

        /**
         * Decrements this {@link Instance}'s reference count, unless it
         * is {@code 0}, and returns the result.
         *
         * <p>If the result of decrementing the reference count is {@code
         * 0}, then this {@link Instance}'s contextual instance is
         * {@linkplain Contextual#destroy(Object, CreationalContext)
         * destroyed}, and its internal object reference is set to {@code
         * null}.  Subsequent invocations of {@link #get()} will throw
         * {@link IllegalStateException}.</p>
         *
         * @return the resulting decremented reference count
         *
         * @exception IllegalStateException if the internal reference
         * count is already less than or equal to zero
         */
        private int decrementReferenceCount() {
            if (this.referenceCount > 0) {
                --this.referenceCount;
                if (this.referenceCount == 0) {
                    this.bean.destroy(this.object, this.creationalContext);
                    if (this.creationalContext != null) {
                        this.creationalContext.release();
                    }
                    this.object = null;
                    this.referenceCount = -1;
                }
            } else {
                throw new IllegalStateException("this.referenceCount <= 0: " + this.referenceCount);
            }
            return this.referenceCount;
        }

    }

}
