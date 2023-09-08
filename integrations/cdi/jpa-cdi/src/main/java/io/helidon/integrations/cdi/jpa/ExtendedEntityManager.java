/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.jpa;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.persistence.EntityManager;

/**
 * A {@link DelegatingEntityManager} created to support extended
 * persistence contexts.
 */
class ExtendedEntityManager extends DelegatingEntityManager {


    /*
     * Instance fields.
     */


    private EntityManager delegate;

    private EntityManager subdelegate;

    private final TransactionSupport transactionSupport;

    private final Set<? extends Annotation> suppliedQualifiers;

    private final boolean isSynchronized;

    private final Instance<Object> instance;

    private final BeanManager beanManager;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link ExtendedEntityManager}.
     *
     * <p>This constructor exists only for proxiable type requirements imposed by CDI. Using it for any other purpose
     * will result in undefined behavior.</p>
     *
     * @deprecated For use by CDI only.
     */
    @Deprecated // For use by CDI only.
    ExtendedEntityManager() {
        super();
        this.transactionSupport = null;
        this.suppliedQualifiers = null;
        this.isSynchronized = false;
        this.instance = null;
        this.beanManager = null;
    }

    /**
     * Creates a new {@link ExtendedEntityManager}.
     *
     * @param instance an {@link Instance} representing the CDI
     * container
     *
     * @param suppliedQualifiers a {@link Set} of qualifier {@link
     * Annotation}s; must not be {@code null}
     *
     * @param beanManager a {@link BeanManager}; must not be {@code
     * null}
     *
     * @exception NullPointerException if any parameter value is
     * {@code null}
     */
    ExtendedEntityManager(final Instance<Object> instance,
                          final Set<? extends Annotation> suppliedQualifiers,
                          final BeanManager beanManager) {
        super();
        this.instance = Objects.requireNonNull(instance);
        this.suppliedQualifiers = Objects.requireNonNull(suppliedQualifiers);
        this.beanManager = Objects.requireNonNull(beanManager);
        this.transactionSupport = Objects.requireNonNull(instance.select(TransactionSupport.class).get());
        if (!transactionSupport.isEnabled()) {
            throw new IllegalArgumentException("!transactionSupport.isEnabled()");
        }
        this.isSynchronized = !suppliedQualifiers.contains(Unsynchronized.Literal.INSTANCE);
        assert
            suppliedQualifiers.contains(Unsynchronized.Literal.INSTANCE)
            || suppliedQualifiers.contains(Synchronized.Literal.INSTANCE)
            : "Unexpected supplied qualifiers: " + suppliedQualifiers;
    }


    /*
     * Instance methods.
     */


    /**
     * Acquires and returns the delegate {@link EntityManager} that
     * this {@link ExtendedEntityManager} must use according to the
     * rules for extended persistence contexts spelled out in the JPA
     * specification.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return the delegate {@link EntityManager}; never {@code null}
     */
    @Override
    @SuppressWarnings("checkstyle:MethodLength")
    protected EntityManager acquireDelegate() {

        // This is very tricky.  Pay attention.
        //
        // An extended EntityManager's persistence context (its "bag"
        // of "managed", not "detached", entities) spans transactions.
        //
        // In this CDI-centric implementation, we make use of
        // jakarta.transaction.TransactionScoped to serve up
        // EntityManager instances in true JTA transaction scope.
        // That means those objects are destroyed when the transaction
        // is destroyed.  Obviously therefore they cannot be used as
        // the sole basis for the transaction-respecting bits of an
        // extended EntityManager.
        //
        // However, an extended EntityManager must "become" the sole
        // EntityManager associated with a JTA transaction, and then
        // must dissociate from it after it has committed or rolled
        // back.
        //
        // So we make use of the fact that the @TransactionScoped bean
        // that has been installed is going to produce instances of
        // CdiTransactionScopedEntityManager.  This
        // DelegatingEntityManager subclass allows you to set _its_
        // delegate, provided it has not yet been set, and allows you
        // to control whether or not its delegate is actually closed
        // when _it_ is closed.  This lets us "tunnel" another
        // EntityManager "into" transaction scope.

        final EntityManager returnValue;

        final Context context = transactionSupport.getContext();
        if (context == null || !context.isActive()) {

            // Our TransactionSupport implementation tells us that a
            // transaction is not in play.

            if (this.delegate == null) {
                assert this.subdelegate == null;

                // For the first time during the lifespan of this
                // object, create its delegate.  It will be a "normal"
                // EntityManager.  We don't set our subdelegate
                // because that is only set in transactional cases
                // (see below).
                this.delegate = EntityManagers.createContainerManagedEntityManager(this.instance, this.suppliedQualifiers);

            } else if (this.delegate instanceof CdiTransactionScopedEntityManager) {

                // This object has participated in transactions
                // before.  The delegate currently installed is a
                // CdiTransactionScopedEntityManager, so that means
                // it's stale (we're NOT in a transaction here,
                // remember, so if we have a
                // CdiTransactionScopedEntityManager it could have
                // only come about as a result of a transaction that
                // committed or rolled back some time ago).
                //
                // (Note that among other things this means we cannot
                // perform any--*any*--operations (including
                // toString(), isOpen(), etc.) on the non-null
                // delegate in this case.  Since the object is a
                // CdiTransactionScopedEntityManager, then we know it
                // came from a TransactionScoped Context, and we know
                // that Context is inactive so the forwarding of the
                // operation through the proxy to the actual object
                // will fail with a ContextNotActiveException.)

                // We know that in order to have assigned a
                // CdiTransactionScopedEntityManager to our delegate
                // field we also had to have assigned a non-null
                // "normal" EntityManager to our subdelegate field
                // (see later in this method for proof).
                assert this.subdelegate != null;
                assert !(this.subdelegate instanceof CdiTransactionScopedEntityManager);
                assert this.subdelegate.isOpen();

                // We are an extended EntityManager, so take that old
                // "normal" EntityManager subdelegate that we know was
                // "tunneled" into our "stale"
                // CdiTransactionScopedEntityManager delegate, and
                // "pull it out" to be our delegate.  We can discard
                // the stale CdiTransactionScopedEntityManager at this
                // point, which we do by just overwriting its
                // reference.
                this.delegate = this.subdelegate;

                // Now set our subdelegate to null to help indicate that
                // we're no longer in a transactional situation.
                this.subdelegate = null;

            }

            // At this point we're not in a transaction, so our
            // delegate is "normal" and not stale, and there's no
            // subdelegate to indicate any kind of a transactional
            // situation.
            assert this.delegate != null;
            assert !(this.delegate instanceof CdiTransactionScopedEntityManager);
            assert this.delegate.isOpen();
            assert this.subdelegate == null;

            returnValue = this.delegate;

        } else {
            // Transaction in play.

            if (this.delegate != null && !this.delegate.isOpen()) {

                // A JTA transaction is in play and we've been through
                // this acquireDelegate() method before at some point
                // in the past.  That's why our delegate is non-null.
                // We know here that it was a transactional delegate
                // that was closed automatically by CDI.  Becase it
                // has been closed, we can't use it again.  But we
                // still need our persistence context that was "in"
                // it.  Fortunately, we saved that off in our
                // subdelegate field.
                assert this.delegate instanceof CdiTransactionScopedEntityManager;
                assert this.subdelegate != null;
                assert !(this.subdelegate instanceof CdiTransactionScopedEntityManager);
                assert this.subdelegate.isOpen();
                this.delegate = this.subdelegate;
                this.subdelegate = null;
            }

            if (!(this.delegate instanceof CdiTransactionScopedEntityManager)) {

                // A JTA transaction is in play and our delegate is
                // either null or a "normal", open EntityManager.  Our
                // subdelegate must be null whenever our delegate is
                // not a CdiTransactionScopedEntityManager.
                assert this.subdelegate == null;
                assert this.delegate == null ? true : this.delegate.isOpen();

                // Since we're in a transaction, we have to look for
                // the @TransactionScoped EntityManager that is
                // associated with the JTA transaction.  So look for
                // an EntityManager bean annotated with, among other
                // possible things, @CdiTransactionScoped
                // and @ContainerManaged.
                final Set<Annotation> selectionQualifiers = new HashSet<>(this.suppliedQualifiers);
                selectionQualifiers.remove(Extended.Literal.INSTANCE);
                selectionQualifiers.remove(JpaTransactionScoped.Literal.INSTANCE);
                selectionQualifiers.remove(NonTransactional.Literal.INSTANCE);
                selectionQualifiers.add(CdiTransactionScoped.Literal.INSTANCE);
                selectionQualifiers.add(ContainerManaged.Literal.INSTANCE);
                final Set<Bean<?>> cdiTransactionScopedEntityManagerBeans =
                    this.beanManager.getBeans(CdiTransactionScopedEntityManager.class,
                                              selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]));
                assert cdiTransactionScopedEntityManagerBeans != null;
                assert !cdiTransactionScopedEntityManagerBeans.isEmpty();
                @SuppressWarnings("unchecked")
                final Bean<?> cdiTransactionScopedEntityManagerBean =
                    (Bean<CdiTransactionScopedEntityManager>) this.beanManager.resolve(cdiTransactionScopedEntityManagerBeans);
                assert cdiTransactionScopedEntityManagerBean != null;
                assert context.getScope().equals(cdiTransactionScopedEntityManagerBean.getScope());

                // Using that bean, check the Context to see if there's
                // already a container-managed EntityManager enrolled in
                // the transaction (without accidentally creating a new
                // one, hence the single-argument Context#get(Contextual)
                // invocation, not the dual-argument
                // Context#get(Contextual, CreationalContext) one).  We
                // have to do this to honor section 7.6.3.1 of the JPA
                // specification.
                final Object existingContainerManagedCdiTransactionScopedEntityManager =
                    context.get(cdiTransactionScopedEntityManagerBean);
                if (existingContainerManagedCdiTransactionScopedEntityManager != null) {
                    // If there IS already a container-managed
                    // EntityManager enrolled in the transaction, we
                    // need to follow JPA section 7.6.3.1 and throw an
                    // analog of EJBException; see
                    // https://github.com/wildfly/wildfly/blob/7f80f0150297bbc418a38e7e23da7cf0431f7c28/jpa/subsystem/src/main/java/org/jboss/as/jpa/container/ExtendedEntityManager.java#L149
                    // as an arbitrary example.
                    //
                    // Revisit: actually, this arbitrary example tests
                    // something completely different; I think this is
                    // a bug in Wildfly.  Section 7.6.3.1 doesn't have
                    // anything to do with transactions, but only with
                    // inheritance of extended persistence contexts
                    // across component boundaries, whether
                    // transactional or not.
                    throw new CreationException(Messages.format("preexistingExtendedEntityManager",
                                                                cdiTransactionScopedEntityManagerBean,
                                                                existingContainerManagedCdiTransactionScopedEntityManager));
                }

                // OK, there's no existing CDI-transaction-scoped
                // EntityManager.  Let's cause one to be created.
                @SuppressWarnings("unchecked")
                final CdiTransactionScopedEntityManager cdiTransactionScopedEntityManager =
                    (CdiTransactionScopedEntityManager)
                    this.beanManager
                        .getReference(cdiTransactionScopedEntityManagerBean,
                                      CdiTransactionScopedEntityManager.class,
                                      this.beanManager.createCreationalContext(cdiTransactionScopedEntityManagerBean));
                assert cdiTransactionScopedEntityManager != null;

                //
                // WARNING to future maintainers: Do NOT call ANY
                // other operations on this
                // cdiTransactionScopedEntityManager until we have
                // installed its delegate!  That includes things like
                // assert cdiTransactionScopedEntityManager.isOpen()!
                //

                // Now make this CdiTransactionScopedEntityManager be
                // our delegate, and "tunnel" our existing delegate
                // "through" to be *its* delegate.
                if (this.delegate == null) {
                    this.subdelegate = EntityManagers.createContainerManagedEntityManager(this.instance, this.suppliedQualifiers);
                } else {
                    assert !(this.delegate instanceof CdiTransactionScopedEntityManager);
                    assert this.subdelegate == null;
                    this.subdelegate = this.delegate;
                }
                assert this.subdelegate != null;
                cdiTransactionScopedEntityManager.setDelegate(this.subdelegate);
                this.delegate = cdiTransactionScopedEntityManager;

                // Section 7.9.1 of the JPA specification requires we
                // join the transaction if we're a synchronized
                // extended EntityManager.
                if (this.isSynchronized) {
                    this.delegate.joinTransaction();
                }

            }
            assert this.delegate != null;
            assert this.delegate.isOpen();
            returnValue = this.delegate;
        }
        return returnValue;
    }

    @Override
    public void close() {
        // Revisit: Wildfly allows end users to close UNSYNCHRONIZED
        // container-managed EntityManagers:
        // https://github.com/wildfly/wildfly/blob/7f80f0150297bbc418a38e7e23da7cf0431f7c28/jpa/subsystem/src/main/java/org/jboss/as/jpa/container/UnsynchronizedEntityManagerWrapper.java#L75-L78
        // I don't know why.  Glassfish does not:
        // https://github.com/javaee/glassfish/blob/f9e1f6361dcc7998cacccb574feef5b70bf84e23/appserver/common/container-common/src/main/java/com/sun/enterprise/container/common/impl/EntityManagerWrapper.java#L752-L761
        throw new IllegalStateException();
    }

    void closeDelegates() {
        if (this.delegate != null && this.delegate.isOpen()) {
            this.delegate.close();
        }
    }

}
