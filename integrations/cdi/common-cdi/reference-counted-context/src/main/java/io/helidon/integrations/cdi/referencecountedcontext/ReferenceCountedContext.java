/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
import javax.enterprise.inject.spi.BeanManager;

/**
 * A somewhat special-purpose {@link AlterableContext} that
 * {@linkplain #destroy(Contextual) destroys} a contextual instance
 * when its thread-specific reference count drops to zero or less than
 * zero.
 *
 * <p>A contextual instance's thread-specific reference count is
 * incremented when either the {@link #get(Contextual)} or {@link
 * #get(Contextual, CreationalContext)} method is called.  It is
 * decremented when the obtained instance is passed to the {@link
 * #decrementReferenceCount(Contextual)} method, which is indirectly
 * and solely responsible for that instance's ultimate removal from
 * this {@link ReferenceCountedContext}.</p>
 *
 * <p>Internally, the {@link #destroy(Contextual)} method simply calls
 * {@link #decrementReferenceCount(Contextual)}.</p>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Instances of this class are safe for concurrent use by multiple
 * threads.</p>
 *
 * @see #decrementReferenceCount(Contextual)
 *
 * @see #destroy(Contextual)
 *
 * @see #get(Contextual, CreationalContext)
 *
 * @see ReferenceCounted
 */
public final class ReferenceCountedContext implements AlterableContext {


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
     * the supplied {@link Contextual}, {@linkplain
     * Contextual#destroy(Object, CreationalContext) destroying} the
     * instance if and only if the reference count becomes less than
     * or equal to zero, and returns the resulting reference count.
     *
     * @param c the {@link Contextual} whose instance's reference
     * count should be decremented; may be {@code null} in which case
     * no action will be taken and {@code 0} will be returned
     *
     * @return the resulting reference count
     *
     * @see Contextual#destroy(Object, CreationalContext)
     *
     * @see #get(Contextual, CreationalContext)
     */
    public int decrementReferenceCount(final Contextual<?> c) {
        return this.decrementReferenceCount(c, 1);
    }

    /**
     * Decrements the reference count of the contextual instance, if
     * any, associated with the combination of the current thread and
     * the supplied {@link Contextual}, {@linkplain
     * Contextual#destroy(Object, CreationalContext) destroying} the
     * instance if and only if the reference count becomes less than
     * or equal to zero, and returns the resulting reference count.
     *
     * <p>Most users will never have to call this method.</p>
     *
     * @param c the {@link Contextual} whose instance's reference
     * count should be decremented; may be {@code null} in which case
     * no action will be taken and {@code 0} will be returned
     *
     * @param amount the amount by which to decrement; must be greater
     * than or (trivially) equal to {@code 0}
     *
     * @return the resulting reference count
     *
     * @exception IllegalArgumentException if {@code amount} is less
     * than {@code 0}
     *
     * @see Contextual#destroy(Object, CreationalContext)
     *
     * @see #get(Contextual, CreationalContext)
     *
     * @see #decrementReferenceCount(Contextual)
     */
    public int decrementReferenceCount(final Contextual<?> c, final int amount) {
        final int returnValue;
        if (amount < 0) {
            throw new IllegalArgumentException("amount < 0: " + amount);
        } else if (c == null) {
            returnValue = 0;
        } else {
            final Map<?, ? extends Instance<?>> instances = ALL_INSTANCES.get().get(this);
            if (instances == null) {
                returnValue = 0;
            } else {
                final Instance<?> instance = instances.get(c);
                if (instance == null) {
                    returnValue = 0;
                } else {
                    // Note that instance.decrementReferenceCount()
                    // will cause c.destroy(theObject,
                    // creationalContext) to be called if needed; no
                    // need to do it explicitly here.
                    returnValue = instance.decrementReferenceCount(amount);
                    if (returnValue <= 0) {
                        instances.remove(c);
                    }
                }
            }
        }
        return returnValue;
    }

    /**
     * Returns the reference count of the contextual instance, if
     * any, associated with the combination of the current thread and
     * the supplied {@link Contextual}.
     *
     * <p>Most users will never have to call this method.</p>
     *
     * <p>This method never returns a negative number.</p>
     *
     * @param c the {@link Contextual} whose instance's reference
     * count should be returned; may be {@code null} in which case
     * {@code 0} will be returned
     *
     * @return the reference count in question; never a negative
     * number
     */
    public int getReferenceCount(final Contextual<?> c) {
        final int returnValue;
        if (c == null) {
            returnValue = 0;
        } else {
            final Map<?, ? extends Instance<?>> instances = ALL_INSTANCES.get().get(this);
            if (instances == null) {
                returnValue = 0;
            } else {
                final Instance<?> instance = instances.get(c);
                if (instance == null) {
                    returnValue = 0;
                } else {
                    returnValue = Math.max(0, instance.getReferenceCount());
                }
            }
        }
        return returnValue;
    }

    /**
     * Calls the {@link #decrementReferenceCount(Contextual)} method
     * with the supplied {@link Contextual}, destroying it if and only
     * if its thread-specific reference count becomes less than or
     * equal to zero.
     *
     * @param contextual the {@link Contextual} to destroy; may be
     * {@code null} in which case no action will be taken
     *
     * @see #decrementReferenceCount(Contextual)
     *
     * @see #get(Contextual, CreationalContext)
     */
    @Override
    public void destroy(final Contextual<?> contextual) {
        this.decrementReferenceCount(contextual);
    }

    /**
     * Returns the contextual instance associated with the current
     * thread and the supplied {@link Contextual}, or {@code null} if
     * no such contextual instance exists.
     *
     * @param contextual the {@link Contextual} in question; may be
     * {@code null} in which case {@code null} wil be returned
     *
     * @return the contextual instance associated with the current
     * thread and the supplied {@link Contextual}, or {@code null}
     */
    @Override
    public <T> T get(final Contextual<T> contextual) {
        return this.get(contextual, null, false);
    }

    /**
     * Returns the contextual instance associated with the current
     * thread and the supplied {@link Contextual}, {@linkplain
     * Contextual#create(CreationalContext) creating} it if necessary.
     *
     * @param contextual the {@link Contextual} in question; may be
     * {@code null} in which case {@code null} wil be returned
     *
     * @param cc a {@link CreationalContext} that will hold dependent
     * instances; may be {@code null}
     *
     * @return the contextual instance associated with the current
     * thread and the supplied {@link Contextual}; may strictly
     * speaking be {@code null} but normally is not
     */
    @Override
    public <T> T get(final Contextual<T> contextual, final CreationalContext<T> cc) {
        return this.get(contextual, cc, true);
    }

    /**
     * Returns the contextual instance associated with the current
     * thread and the supplied {@link Contextual}, {@linkplain
     * Contextual#create(CreationalContext) creating} it if the {@code
     * maybeCreate} parameter is {@code true}.
     *
     * @param <T> the type of the contextual instance to get
     *
     * @param contextual the {@link Contextual} in question; may be
     * {@code null} in which case {@code null} wil be returned
     *
     * @param cc a {@link CreationalContext} that will hold dependent
     * instances; may be {@code null}
     *
     * @param maybeCreate whether or not to create a new contextual
     * instance if required
     *
     * @return the possibly newly-created contextual instance
     * associated with the current thread and the supplied {@link
     * Contextual}, or {@code null}
     */
    private <T> T get(final Contextual<T> contextual, final CreationalContext<T> cc, final boolean maybeCreate) {
        final T returnValue;
        if (contextual == null) {
            returnValue = null;
        } else {
            final Map<ReferenceCountedContext, Map<Contextual<?>, Instance<?>>> allInstances = ALL_INSTANCES.get();
            assert allInstances != null;
            Map<Contextual<?>, Instance<?>> instances = allInstances.get(this);
            if (instances == null && maybeCreate) {
                instances = new HashMap<>();
                allInstances.put(this, instances);
            }
            if (instances == null) {
                returnValue = null;
            } else {
                @SuppressWarnings("unchecked")
                final Instance<T> temp = (Instance<T>) instances.get(contextual);
                Instance<T> instance = temp;
                if (instance == null) {
                    if (maybeCreate) {
                        instance = new Instance<T>(contextual, cc);
                        instances.put(contextual, instance);
                        returnValue = instance.get();
                    } else {
                        returnValue = null;
                    }
                } else {
                    returnValue = instance.get();
                }
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
     * Static methods.
     */


    /**
     * Returns the sole {@link ReferenceCountedContext} that is
     * registered with the supplied {@link BeanManager}.
     *
     * <p>Strictly speaking, this method may return {@code null} if
     * the {@link ReferenceCountedExtension} has been deliberately
     * disabled and hence has not had a chance to {@linkplain
     * javax.enterprise.inject.spi.AfterBeanDiscovery#addContext(Context)
     * install} a {@link ReferenceCountedContext}.  In all normal
     * usage this method will not return {@code null}.</p>
     *
     * @param beanManager the {@link BeanManager} whose {@link
     * ReferenceCountedContext} should be returned; must not be {@code
     * null}
     *
     * @return a {@link ReferenceCountedContext}, or {@code null} in
     * exceptionally rare situations
     *
     * @exception NullPointerException if {@code beanManager} is
     * {@code null}
     */
    public static ReferenceCountedContext getInstanceFrom(final BeanManager beanManager) {
        Objects.requireNonNull(beanManager);
        return (ReferenceCountedContext) beanManager.getContext(ReferenceCounted.class);
    }


    /*
     * Inner and nested classes.
     */


    /**
     * A coupling of an object (a contextual instance), the {@link
     * CreationalContext} in effect when it was created, the {@link
     * Contextual} that {@linkplain
     * Contextual#create(CreationalContext) created} it, and a
     * reference count.
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
         * ReferenceCountedContext#get(Contextual, CreationalContext)}
         * and {@link ReferenceCountedContext#get(Contextual)} have
         * been called.
         *
         * <p>If this field's value is less than zero, then {@link
         * #get()} invocations will throw {@link
         * IllegalStateException}.</p>
         *
         * @see #decrementReferenceCount(int)
         *
         * @see #get()
         */
        private int referenceCount;

        /**
         * The {@link CreationalContext} supplied to this {@link
         * Instance} at construction time.
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
        private final Contextual<T> contextual;


        /*
         * Constructors.
         */


        /**
         * Creates a new {@link Instance}.
         *
         * @param contextual the {@link Contextual} that creates and
         * destroys contextual instances; must not be {@code null}
         *
         * @param creationalContext the {@link CreationalContext} that
         * will track dependent instances of the contextual instance;
         * may be {@code null}
         *
         * @exception NullPointerException if {@code contextual} is
         * {@code null}
         *
         * @see Contextual
         *
         * @see CreationalContext
         *
         * @see Contextual#create(CreationalContext)
         */
        private Instance(final Contextual<T> contextual, final CreationalContext<T> creationalContext) {
            super();
            this.contextual = Objects.requireNonNull(contextual);
            this.creationalContext = creationalContext;
            this.object = this.contextual.create(creationalContext);
        }


        /*
         * Instance methods.
         */


        /**
         * Returns the contextual instance this {@link Instance} wraps
         * and increments its reference count.
         *
         * <p>This method may return {@code null} if the contextual
         * instance this {@link Instance} was created with was {@code
         * null} at construction time.</p>
         *
         * @return the contextual instance this {@link Instance}
         * wraps, or {@code null}
         *
         * @exception IllegalStateException if the reference count
         * internally is less than zero, such as when an invocation of
         * {@link #decrementReferenceCount(int)} has resulted in
         * destruction
         */
        private T get() {
            if (this.referenceCount < 0) {
                throw new IllegalStateException("this.referenceCount < 0: " + this.referenceCount);
            } else if (this.referenceCount < Integer.MAX_VALUE) {
                ++this.referenceCount;
            }
            return this.object;
        }

        private int getReferenceCount() {
            return this.referenceCount;
        }

        /**
         * Decrements this {@link Instance}'s reference count by the
         * supplied amount, unless it is already {@code 0}, and
         * returns the result.
         *
         * <p>If the result of decrementing the reference count is
         * less than or equal to {@code 0}, then this {@link
         * Instance}'s contextual instance is {@linkplain
         * Contextual#destroy(Object, CreationalContext) destroyed},
         * and its internal object reference is set to {@code null}.
         * Subsequent invocations of {@link #get()} will throw {@link
         * IllegalStateException}.</p>
         *
         * <p>Internally, if a reference count ever drops below {@code
         * 0}, it is set to {@code 0}.</p>
         *
         * @param amount the amount to decrement by; must be greater
         * than or (trivially) equal to {@code 0}
         *
         * @return the resulting decremented reference count
         *
         * @exception IllegalArgumentException if {@code amount} is
         * less than {@code 0}
         *
         * @exception IllegalStateException if the internal reference
         * count is already less than or equal to zero
         */
        private int decrementReferenceCount(final int amount) {
            if (amount < 0) {
                throw new IllegalArgumentException("amount < 0: " + amount);
            } else if (this.referenceCount > 0) {
                this.referenceCount = Math.max(0, this.referenceCount - amount);
                if (this.referenceCount == 0) {
                    this.contextual.destroy(this.object, this.creationalContext);
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
