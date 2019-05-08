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
package io.helidon.integrations.cdi.jpa.weld;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceUnit;
import javax.persistence.SynchronizationType;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;

import org.jboss.weld.injection.spi.JpaInjectionServices;
import org.jboss.weld.injection.spi.ResourceReference;
import org.jboss.weld.injection.spi.ResourceReferenceFactory;
import org.jboss.weld.manager.api.ExecutorServices;
import org.jboss.weld.manager.api.WeldManager;

import static javax.persistence.spi.PersistenceUnitTransactionType.JTA;
import static javax.persistence.spi.PersistenceUnitTransactionType.RESOURCE_LOCAL;

/**
 * A {@link JpaInjectionServices} implementation that integrates JPA
 * functionality into Weld-based CDI environments.
 *
 * @see JpaInjectionServices
 */
final class WeldJpaInjectionServices implements JpaInjectionServices {


    /*
     * Static fields.
     */


    /*
     * Weld instantiates this class exactly three (!) times during
     * normal execution (see https://issues.jboss.org/browse/WELD-2563
     * for details).  Only one of those instances (the first) is
     * actually used to produce EntityManagers and
     * EntityManagerFactories; the other two are discarded.  The
     * static instance and underway fields ensure that truly only one
     * instance processes all incoming calls, and that it is the one
     * that is actually tracked and stored by Weld itself in the
     * return value of the WeldManager#getServices() method.
     *
     * See the underway() method as well.
     */

    /**
     * The single officially sanctioned instance of this class.
     *
     * <p>This field may be {@code null}.</p>
     *
     * @see <a href="https://issues.jboss.org/browse/WELD-2563"
     * target="_parent">WELD-2563</a>
     *
     * @see #getInstance()
     */
    private static volatile WeldJpaInjectionServices instance;

    /**
     * Whether a "business" method of this class has been invoked or
     * not.
     *
     * @see <a href="https://issues.jboss.org/browse/WELD-2563"
     * target="_parent">WELD-2563</a>
     */
    private static volatile boolean underway;

    /**
     * The {@link Logger} for use by all instances of this class.
     *
     * <p>This field is never {@code null}.</p>
     */
    private static final Logger LOGGER = Logger.getLogger(WeldJpaInjectionServices.class.getName(), "messages");


    /*
     * Instance fields.
     */


    /**
     * A {@link Set} of {@link EntityManager}s that have been created
     * as container-managed {@link EntityManager}s, i.e. not
     * application-managed ones.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>The {@link Set} assigned to this field is safe for
     * concurrent usage by multiple threads.</p>
     */
    private final Set<EntityManager> containerManagedEntityManagers;

    /**
     * A {@link Map} of {@link EntityManagerFactory} instances indexed
     * by names of persistence units.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>Synchronization on this field is required for concurrent
     * thread access.</p>
     */
    // @GuardedBy("this")
    private volatile Map<String, EntityManagerFactory> emfs;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link WeldJpaInjectionServices}.
     *
     * <p>Oddly, the fact that this constructor is {@code private}
     * does not prevent Weld from loading it as a service.  This is an
     * unexpected bonus as nothing about this class should be {@code
     * public}.</p>
     */
    private WeldJpaInjectionServices() {
        super();
        synchronized (WeldJpaInjectionServices.class) {
            // See https://issues.jboss.org/browse/WELD-2563.  Make sure
            // only the first instance is "kept" as it's the one tracked by
            // WeldManager's ServiceRegistry.  The others are discarded.
            if (instance == null) {
                assert !underway;
                instance = this;
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.logp(Level.WARNING,
                                WeldJpaInjectionServices.class.getName(),
                                "<init>",
                                "experimental");
                }
            } else if (underway) {
                throw new IllegalStateException();
            }
        }
        this.containerManagedEntityManagers = ConcurrentHashMap.newKeySet();
    }

    /**
     * Records the fact that a significant method has been invoked.
     *
     * @see <a href="https://issues.jboss.org/browse/WELD-2563"
     * target="_parent">WELD-2563</a>
     */
    private static synchronized void underway() {
        underway = true;
    }

    /**
     * Returns the only instance of this class.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return the same non-{@code null} {@link WeldJpaInjectionServices}
     * when invoked
     *
     * @see <a href="https://issues.jboss.org/browse/WELD-2563"
     * target="_parent">WELD-2563</a>
     */
    static synchronized WeldJpaInjectionServices getInstance() {
        return instance;
    }

    /**
     * Called by the ({@code private}) {@code TransactionObserver}
     * class when a JTA transaction is begun.
     *
     * <p>The Narayana CDI integration this class is often deployed
     * with will fire such events.  These events serve as an
     * indication that a call to {@link
     * javax.transaction.TransactionManager#begin()} has been
     * made.</p>
     *
     * <p>{@link EntityManager}s created by this class will have their
     * {@link EntityManager#joinTransaction()} methods called if the
     * supplied object is non-{@code null}.</p>
     */
    void jtaTransactionBegun() {
        final String cn = WeldJpaInjectionServices.class.getName();
        final String mn = "jtaTransactionBegun";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn);
        }

        assert this == instance;
        for (final EntityManager containerManagedEntityManager : this.containerManagedEntityManagers) {
            assert containerManagedEntityManager != null;
            final Map<String, Object> properties = containerManagedEntityManager.getProperties();
            final Object synchronizationType;
            if (properties == null) {
                synchronizationType = null;
            } else {
                synchronizationType = properties.get(SynchronizationType.class.getName());
            }
            if (SynchronizationType.SYNCHRONIZED.equals(synchronizationType)) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.logp(Level.FINE, cn, mn, "{0} joining transaction", containerManagedEntityManager);
                }
                containerManagedEntityManager.joinTransaction();
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Called by the ({@code private}) {@code TransactionObserver}
     * class when a JTA transaction has ended, either successfully or
     * unsuccessfully.
     *
     * <p>The Narayana CDI integration this class is often deployed
     * with will fire such events.  These events serve as an
     * indication that a call to {@link
     * javax.transaction.TransactionManager#commit()} or {@link
     * javax.transaction.TransactionManager#rollback()} has been
     * made.</p>
     */
    void jtaTransactionEnded() {
        final String cn = WeldJpaInjectionServices.class.getName();
        final String mn = "jtaTransactionEnded";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn);
        }

        // This method is reserved for future use.

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Returns a {@link ResourceReferenceFactory} whose {@link
     * ResourceReferenceFactory#createResource()} method will be
     * invoked appropriately by Weld later.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @param injectionPoint the {@link InjectionPoint} annotated with
     * {@link PersistenceContext}; must not be {@code null}
     *
     * @return a non-{@code null} {@link ResourceReferenceFactory}
     * whose {@link ResourceReferenceFactory#createResource()} method
     * will create {@link EntityManager} instances
     *
     * @exception NullPointerException if {@code injectionPoint} is
     * {@code null}
     *
     * @see ResourceReferenceFactory#createResource()
     */
    @Override
    public ResourceReferenceFactory<EntityManager> registerPersistenceContextInjectionPoint(final InjectionPoint injectionPoint) {
        final String cn = WeldJpaInjectionServices.class.getName();
        final String mn = "registerPersistenceContextInjectionPoint";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, injectionPoint);
        }
        underway();
        assert this == instance;
        final ResourceReferenceFactory<EntityManager> returnValue;
        Objects.requireNonNull(injectionPoint);
        final Annotated annotatedMember = injectionPoint.getAnnotated();
        assert annotatedMember != null;
        final PersistenceContext persistenceContextAnnotation = annotatedMember.getAnnotation(PersistenceContext.class);
        if (persistenceContextAnnotation == null) {
            throw new IllegalArgumentException("injectionPoint.getAnnotated().getAnnotation(PersistenceContext.class) == null");
        }
        final String name;
        final String n = persistenceContextAnnotation.unitName();
        if (n.isEmpty()) {
            if (annotatedMember instanceof AnnotatedField) {
                name = ((AnnotatedField<?>) annotatedMember).getJavaMember().getName();
            } else {
                name = n;
            }
        } else {
            name = n;
        }
        final SynchronizationType synchronizationType = persistenceContextAnnotation.synchronization();
        assert synchronizationType != null;
        synchronized (this) {
            if (this.emfs == null) {
                this.emfs = new ConcurrentHashMap<>();
            }
        }
        returnValue = () -> new EntityManagerResourceReference(name, synchronizationType);
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    /**
     * Returns a {@link ResourceReferenceFactory} whose {@link
     * ResourceReferenceFactory#createResource()} method will be
     * invoked appropriately by Weld later.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @param ip the {@link InjectionPoint} annotated with {@link
     * PersistenceUnit}; must not be {@code null}
     *
     * @return a non-{@code null} {@link ResourceReferenceFactory}
     * whose {@link ResourceReferenceFactory#createResource()} method
     * will create {@link EntityManagerFactory} instances
     *
     * @exception NullPointerException if {@code ip} is
     * {@code null}
     *
     * @see ResourceReferenceFactory#createResource()
     */
    @Override
    public ResourceReferenceFactory<EntityManagerFactory> registerPersistenceUnitInjectionPoint(final InjectionPoint ip) {
        final String cn = WeldJpaInjectionServices.class.getName();
        final String mn = "registerPersistenceUnitInjectionPoint";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, ip);
        }

        underway();
        assert this == instance;
        final ResourceReferenceFactory<EntityManagerFactory> returnValue;
        Objects.requireNonNull(ip);
        final Annotated annotatedMember = ip.getAnnotated();
        assert annotatedMember != null;
        final PersistenceUnit persistenceUnitAnnotation = annotatedMember.getAnnotation(PersistenceUnit.class);
        if (persistenceUnitAnnotation == null) {
            throw new IllegalArgumentException("ip.getAnnotated().getAnnotation(PersistenceUnit.class) == null");
        }
        final String name;
        final String n = persistenceUnitAnnotation.unitName();
        if (n.isEmpty()) {
            if (annotatedMember instanceof AnnotatedField) {
                name = ((AnnotatedField<?>) annotatedMember).getJavaMember().getName();
            } else {
                name = n;
            }
        } else {
            name = n;
        }
        synchronized (this) {
            if (this.emfs == null) {
                this.emfs = new ConcurrentHashMap<>();
            }
        }
        returnValue = () -> new EntityManagerFactoryResourceReference(this.emfs, name);

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    /**
     * Invoked by Weld automatically to clean up any resources held by
     * this class.
     */
    @Override
    public void cleanup() {
        // cleanup() can get invoked multiple times at will by Weld.
        // Specifically, the same Service instance can be stored in
        // multiple BeanManagerImpls, and each one can call its
        // cleanup() method, so it must be idempotent.
        //
        // See
        // https://github.com/weld/core/blob/06fcaf4a6f625f101be5804208c1eb3a32884773/impl/src/main/java/org/jboss/weld/Container.java#L143-L145
        // and
        // https://github.com/weld/core/blob/06fcaf4a6f625f101be5804208c1eb3a32884773/impl/src/main/java/org/jboss/weld/manager/BeanManagerImpl.java#L1173.
        if (underway) {
            assert this == instance;

            // this.containerManagedEntityManagers should be empty
            // already.  If for some reason it is not, we just clear()
            // it (rather than, say, calling em.close() on each
            // element).  This is for two reasons: one, we're being
            // cleaned up so the whole container is going down anyway.
            // Two, we're going to close() all EntityManagerFactories
            // which should also do the job since an
            // EntityManagerFactory's EntityManagers are supposed to
            // be closed when the EntityManagerFactory is closed.
            this.containerManagedEntityManagers.clear();

            final Map<? extends String, ? extends EntityManagerFactory> emfs = this.emfs;
            if (emfs != null && !emfs.isEmpty()) {
                final Collection<? extends EntityManagerFactory> values = emfs.values();
                assert values != null;
                assert !values.isEmpty();
                final Iterator<? extends EntityManagerFactory> iterator = values.iterator();
                assert iterator != null;
                assert iterator.hasNext();
                while (iterator.hasNext()) {
                    final EntityManagerFactory emf = iterator.next();
                    assert emf != null;
                    if (emf.isOpen()) {
                        emf.close();
                    }
                    iterator.remove();
                }
            }
        }
        assert this.containerManagedEntityManagers.isEmpty();
        assert this.emfs == null || this.emfs.isEmpty();
        synchronized (WeldJpaInjectionServices.class) {
            underway = false;
            instance = null;
        }
    }

    /**
     * Calls the {@link
     * #registerPersistenceContextInjectionPoint(InjectionPoint)}
     * method and invokes {@link ResourceReference#getInstance()} on
     * its return value and returns the result.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @param injectionPoint an {@link InjectionPoint} annotated with
     * {@link PersistenceContext}; must not be {@code null}
     *
     * @return a non-{@code null} {@link EntityManager}
     *
     * @see #registerPersistenceContextInjectionPoint(InjectionPoint)
     *
     * @deprecated
     */
    @Deprecated
    public EntityManager resolvePersistenceContext(final InjectionPoint injectionPoint) {
        return this.registerPersistenceContextInjectionPoint(injectionPoint).createResource().getInstance();
    }

    /**
     * Calls the {@link
     * #registerPersistenceUnitInjectionPoint(InjectionPoint)} method
     * and invokes {@link ResourceReference#getInstance()} on its
     * return value and returns the result.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @param injectionPoint an {@link InjectionPoint} annotated with
     * {@link PersistenceUnit}; must not be {@code null}
     *
     * @return a non-{@code null} {@link EntityManagerFactory}
     *
     * @see #registerPersistenceUnitInjectionPoint(InjectionPoint)
     *
     * @deprecated
     */
    @Deprecated
    public EntityManagerFactory resolvePersistenceUnit(final InjectionPoint injectionPoint) {
        return this.registerPersistenceUnitInjectionPoint(injectionPoint).createResource().getInstance();
    }


    /*
     * Static methods.
     */


    /**
     * Returns a {@link PersistenceProvider} for the supplied {@link
     * PersistenceUnitInfo}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @param persistenceUnitInfo the {@link PersistenceUnitInfo} in
     * question; must not be {@code null}
     *
     * @return a non-{@code null} {@link PersistenceProvider}
     *
     * @exception NullPointerException if {@code persistenceUnitInfo}
     * was {@code null}
     *
     * @exception
     * javax.enterprise.inject.UnsatisfiedResolutionException if no
     * {@link PersistenceProvider} could be found
     *
     * @exception javax.enterprise.inject.AmbiguousResolutionException
     * if there were many possible {@link PersistenceProvider}s that
     * could be returned
     *
     * @exception ReflectiveOperationException if there was a
     * reflection-related error
     */
    private static PersistenceProvider getPersistenceProvider(final PersistenceUnitInfo persistenceUnitInfo)
        throws ReflectiveOperationException {
        final String providerClassName = Objects.requireNonNull(persistenceUnitInfo).getPersistenceProviderClassName();
        final PersistenceProvider persistenceProvider;
        final CDI<Object> cdi = CDI.current();
        assert cdi != null;
        if (providerClassName == null) {
            persistenceProvider = cdi.select(PersistenceProvider.class).get();
        } else {
            persistenceProvider =
                (PersistenceProvider) cdi.select(Class.forName(providerClassName,
                                                               true,
                                                               Thread.currentThread().getContextClassLoader())).get();
        }
        return persistenceProvider;
    }

    /**
     * Given the name of a persistence unit, uses a {@link
     * BeanManager} internally to locate a {@link PersistenceUnitInfo}
     * qualified with a {@link Named} annotation that {@linkplain
     * Named#value() has the same name} as the supplied {@code name},
     * and returns it.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>If there is only one {@link PersistenceUnitInfo} present in
     * the CDI container, then it will be returned by this method when
     * it is invoked, regardless of the value of the {@code name}
     * parameter.</p>
     *
     * @param name the name of the {@link PersistenceUnitInfo} to
     * return; may be effectively ignored in some cases; must not be
     * {@code null}
     *
     * @return a non-{@code null} {@link PersistenceUnitInfo}, which
     * may not have the same name as that which was requested if it
     * was the only such {@link PersistenceUnitInfo} in the CDI
     * container
     *
     * @exception NullPointerException if {@code name} is {@code null}
     *
     * @exception javax.enterprise.inject.AmbiguousResolutionException
     * if there somehow was more than one {@link PersistenceUnitInfo}
     * available
     *
     * @exception
     * javax.enterprise.inject.UnsatisfiedResolutionException if there
     * were no {@link PersistenceUnitInfo} instances available
     */
    private static PersistenceUnitInfo getPersistenceUnitInfo(final String name) {
        final String cn = WeldJpaInjectionServices.class.getName();
        final String mn = "getPersistenceUnitInfo";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, name);
        }

        Objects.requireNonNull(name);
        final CDI<Object> cdi = CDI.current();
        assert cdi != null;
        final BeanManager beanManager = cdi.getBeanManager();
        assert beanManager != null;
        final Named named = NamedLiteral.of(name);
        assert named != null;
        Set<Bean<?>> beans = beanManager.getBeans(PersistenceUnitInfo.class, named);
        final boolean warn;
        if (beans == null || beans.isEmpty()) {
            beans = beanManager.getBeans(PersistenceUnitInfo.class, Any.Literal.INSTANCE);
            warn = LOGGER.isLoggable(Level.WARNING) && beans != null && !beans.isEmpty();
        } else {
            warn = false;
        }
        if (beans == null || beans.isEmpty()) {
            // Let CDI blow up in whatever way it does here.
            cdi.select(PersistenceUnitInfo.class, named).get();
            throw new AssertionError();
        }
        Bean<?> bean = null;
        final int size = beans.size();
        assert size > 0;
        switch (size) {
        case 1:
            // We either got the explicit one we asked for
            // (e.g. "dev"), or the only one there was (we asked for
            // "dev"; the only one that was there was "test"). We may
            // need to revisit this; this may be *too* convenient.
            bean = beans.iterator().next();
            break;
        default:
            bean = beanManager.resolve(beans);
            break;
        }
        final PersistenceUnitInfo returnValue =
            (PersistenceUnitInfo) beanManager.getReference(bean,
                                                           PersistenceUnitInfo.class,
                                                           beanManager.createCreationalContext(bean));
        if (warn) {
            LOGGER.logp(Level.WARNING, cn, mn,
                        "persistenceUnitNameMismatch",
                        new Object[] {returnValue, returnValue.getPersistenceUnitName(), name});
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    /**
     * Given a {@link Map} of {@link EntityManagerFactory} instances
     * indexed by their persistence unit names, an optional {@link
     * PersistenceUnitInfo} and the name of a persistence unit,
     * returns a suitable {@link EntityManagerFactory} for the implied
     * persistence unit, creating it if necessary.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>The contents of the supplied {@link Map} may be altered by
     * this method.</p>
     *
     * @param emfs a {@link Map} of {@link EntityManagerFactory}
     * instances indexed by their persistence unit names; must not be
     * {@code null} but may be {@linkplain Map#isEmpty() empty}
     *
     * @param info a {@link PersistenceUnitInfo}; may be {@code null}
     * in which case the supplied {@code name} must not be {@code
     * null}
     *
     * @param name the name of the persistence unit; must not be
     * {@code null}; if the supplied {@link PersistenceUnitInfo} is
     * not {@code null} then {@linkplain
     * PersistenceUnitInfo#getPersistenceUnitName() its name} should
     * be equal to this value, but is not required to be
     *
     * @return a non-{@code null} {@link EntityManagerFactory}
     *
     * @exception NullPointerException if either {@code emfs} or
     * {@code name} is {@code null}
     *
     * @see #computeEntityManagerFactory(PersistenceUnitInfo, String,
     * EntityManagerFactory)
     */
    private static EntityManagerFactory computeEntityManagerFactory(final Map<String, EntityManagerFactory> emfs,
                                                                    final PersistenceUnitInfo info,
                                                                    final String name) {
        final String cn = WeldJpaInjectionServices.class.getName();
        final String mn = "computeEntityManagerFactory";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {emfs, info, name});
        }

        Objects.requireNonNull(emfs);
        Objects.requireNonNull(name);

        final EntityManagerFactory returnValue = emfs.compute(name, (n, emf) -> computeEntityManagerFactory(info, n, emf));

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    /**
     * Returns the supplied {@link EntityManagerFactory}, if it is
     * non-{@code null} and {@linkplain EntityManagerFactory#isOpen()
     * open}, or creates a new one and returns it.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>If creation is called for, then the supplied {@link
     * PersistenceUnitInfo}'s {@linkplain
     * PersistenceUnitInfo#getTransactionType() affiliated
     * <code>PersistenceUnitTransactionType</code>} is checked to see
     * if it is {@link
     * javax.persistence.spi.PersistenceUnitTransactionType#RESOURCE_LOCAL
     * RESOURCE_LOCAL}.  If it is, then creation occurs by an
     * invocation of the {@link
     * Persistence#createEntityManagerFactory(String)} method.
     * Otherwise, it occurs by an invocation of the {@link
     * #createContainerManagedEntityManagerFactory(PersistenceUnitInfo)}
     * method.</p>
     *
     * @param info a {@link PersistenceUnitInfo} describing a
     * persistence unit; may be {@code null}
     *
     * @param name the name of the persistence unit; must not be
     * {@code null}
     *
     * @param existing an {@link EntityManagerFactory} that was
     * already associated with the supplied {@code name}; may be
     * {@code null}
     *
     * @return the supplied {@link EntityManagerFactory} if it is
     * non-{@code null}, or a new one; never {@code null}
     *
     * @exception NullPointerException if {@code name} is {@code null}
     *
     * @exception PersistenceException if a persistence-related error
     * occurs
     *
     * @see #createContainerManagedEntityManagerFactory(PersistenceUnitInfo)
     */
    private static EntityManagerFactory computeEntityManagerFactory(final PersistenceUnitInfo info,
                                                                    final String name,
                                                                    final EntityManagerFactory existing) {
        final String cn = WeldJpaInjectionServices.class.getName();
        final String mn = "computeEntityManagerFactory";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {info, name, existing});
        }

        final EntityManagerFactory returnValue;
        if (existing == null) {
            if (isResourceLocal(info)) {
                returnValue = Persistence.createEntityManagerFactory(name);
            } else {
                EntityManagerFactory temp = null;
                try {
                    temp = createContainerManagedEntityManagerFactory(info);
                } catch (final ReflectiveOperationException reflectiveOperationException) {
                    throw new PersistenceException(reflectiveOperationException.getMessage(), reflectiveOperationException);
                } finally {
                    returnValue = temp;
                }
            }
        } else {
            returnValue = existing;
            if (LOGGER.isLoggable(Level.WARNING) && !isResourceLocal(info)) {
                final Map<String, Object> properties = existing.getProperties();
                if (properties == null
                    || !Boolean.TRUE.equals(properties.get("io.helidon.integrations.cdi.jpa.weld.containerManaged"))) {
                    LOGGER.logp(Level.WARNING, cn, mn,
                                "transactionTypeMismatch",
                                new Object[] {name, returnValue});
                }
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    /**
     * Creates an {@link EntityManagerFactory} suitable for the
     * supplied {@link PersistenceUnitInfo}, {@linkplain
     * PersistenceProvider#createContainerEntityManagerFactory(PersistenceUnitInfo,
     * Map) following the JPA 2.2 specification}.
     *
     * <p>This method returns a new {@link EntityManagerFactory} each
     * time it is invoked.</p>
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @param info the {@link PersistenceUnitInfo} describing the
     * persistence unit; must not be {@code null}; should have an
     * {@linkplain PersistenceUnitInfo#getTransactionType() affiliated
     * <code>PersistenceUnitTransactionType</code>} equal to {@link
     * javax.persistence.spi.PersistenceUnitTransactionType#JTA JTA}
     *
     * @return a new {@link EntityManagerFactory}; never {@code null}
     *
     * @exception NullPointerException if {@code info} is {@code null}
     *
     * @exception PersistenceException if a persistence-related error
     * occurs
     *
     * @exception ReflectiveOperationException if a reflection-related
     * error occurs
     *
     * @see
     * PersistenceProvider#createContainerEntityManagerFactory(PersistenceUnitInfo,
     * Map)
     */
    private static EntityManagerFactory createContainerManagedEntityManagerFactory(final PersistenceUnitInfo info)
        throws ReflectiveOperationException {
        final String cn = WeldJpaInjectionServices.class.getName();
        final String mn = "createContainerManagedEntityManagerFactory";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, info);
        }

        Objects.requireNonNull(info);
        final PersistenceProvider persistenceProvider = getPersistenceProvider(info);
        assert persistenceProvider != null;
        final CDI<Object> cdi = CDI.current();
        assert cdi != null;
        final BeanManager beanManager = cdi.getBeanManager();
        assert beanManager != null;
        final Map<String, Object> properties = new HashMap<>();
        properties.put("io.helidon.integrations.cdi.jpa.weld.containerManaged", Boolean.TRUE);
        properties.put("javax.persistence.bean.manager", beanManager);
        Class<?> validatorFactoryClass = null;
        try {
            validatorFactoryClass = Class.forName("javax.validation.ValidatorFactory");
        } catch (final ClassNotFoundException classNotFoundException) {

        }
        if (validatorFactoryClass != null) {
            final Bean<?> vfb = getValidatorFactoryBean(beanManager, validatorFactoryClass);
            if (vfb != null) {
                final CreationalContext<?> cc = beanManager.createCreationalContext(vfb);
                properties.put("javax.persistence.validation.factory", beanManager.getReference(vfb, validatorFactoryClass, cc));
            }
        }
        final EntityManagerFactory returnValue = persistenceProvider.createContainerEntityManagerFactory(info, properties);

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    /**
     * Returns a {@link Bean} that can {@linkplain
     * Bean#create(CreationalContext) create} a {@link
     * javax.validation.ValidatorFactory}, or {@code null} if no such
     * {@link Bean} is available.
     *
     * <p>This method may return {@code null}.</p>
     *
     * @param beanManager the {@link BeanManager} in effect; may be
     * {@code null} in which case {@code null} will be returned
     *
     * @param validatorFactoryClass a {@link Class}; may be {@code
     * null}; if not {@linkplain Class#getName() named} {@code
     * javax.validation.ValidatorFactory} then {@code null} will be
     * returned
     *
     * @return a {@link Bean} that can {@linkplain
     * Bean#create(CreationalContext) create} a {@link
     * javax.validation.ValidatorFactory}, or {@code null}
     */
    private static Bean<?> getValidatorFactoryBean(final BeanManager beanManager,
                                                   final Class<?> validatorFactoryClass) {
        Bean<?> returnValue = null;
        if (beanManager != null
            && validatorFactoryClass != null
            && "javax.validation.ValidatorFactory".equals(validatorFactoryClass.getName())) {
            final Set<Bean<?>> beans = beanManager.getBeans(validatorFactoryClass);
            if (beans != null && !beans.isEmpty()) {
                returnValue = beanManager.resolve(beans);
            }
        }
        return returnValue;
    }

    /**
     * Returns {@code true} if and only if the supplied {@link
     * PersistenceUnitInfo} is {@code null} or has an {@linkplain
     * PersistenceUnitInfo#getTransactionType() affiliated
     * <code>PersistenceUnitTransactionType</code>} equal to {@link
     * javax.persistence.spi.PersistenceUnitTransactionType#RESOURCE_LOCAL
     * RESOURCE_LOCAL}.
     *
     * @param persistenceUnitInfo the {@link PersistenceUnitInfo} to
     * test; may be {@code null} in which case {@code true} will be
     * returned
     *
     * @return {@code true} if and only if the supplied {@link
     * PersistenceUnitInfo} is {@code null} or has an {@linkplain
     * PersistenceUnitInfo#getTransactionType() affiliated
     * <code>PersistenceUnitTransactionType</code>} equal to {@link
     * javax.persistence.spi.PersistenceUnitTransactionType#RESOURCE_LOCAL
     * RESOURCE_LOCAL}
     */
    private static boolean isResourceLocal(final PersistenceUnitInfo persistenceUnitInfo) {
        return persistenceUnitInfo == null || RESOURCE_LOCAL.equals(persistenceUnitInfo.getTransactionType());
    }


    /*
     * Inner and nested classes.
     */


    private static final class EntityManagerFactoryResourceReference implements ResourceReference<EntityManagerFactory> {

        private final Map<String, EntityManagerFactory> emfs;

        private final String name;

        private final PersistenceUnitInfo persistenceUnitInfo;

        private EntityManagerFactoryResourceReference(final Map<String, EntityManagerFactory> emfs,
                                                      final String name) {
            super();
            final String cn = EntityManagerFactoryResourceReference.class.getName();
            final String mn = "<init>";
            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.entering(cn, mn, new Object[] {emfs, name});
            }

            this.emfs = Objects.requireNonNull(emfs);
            this.name = Objects.requireNonNull(name);
            this.persistenceUnitInfo = getPersistenceUnitInfo(name);

            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.exiting(cn, mn);
            }
        }

        @Override
        public EntityManagerFactory getInstance() {
            final String cn = EntityManagerFactoryResourceReference.class.getName();
            final String mn = "getInstance";
            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.entering(cn, mn);
            }

            // See https://developer.jboss.org/message/984489#984489;
            // there is no contract governing whether, for example, an
            // EntityManagerFactory should be created from within
            // ResourceReference#getInstance() or from within
            // ResourceReferenceFactory#createResource().  The
            // maintainers of Weld and CDI suggest following what
            // Wildfly does, as it is most likely (!) to be correct.
            // So that's what we do.
            final EntityManagerFactory returnValue = computeEntityManagerFactory(emfs, this.persistenceUnitInfo, this.name);

            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.exiting(cn, mn, returnValue);
            }
            return returnValue;
        }

        @Override
        public void release() {
            final String cn = EntityManagerFactoryResourceReference.class.getName();
            final String mn = "release";
            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.entering(cn, mn);
            }

            final EntityManagerFactory emf = this.emfs.remove(this.name);
            if (emf != null && emf.isOpen()) {
                emf.close();
            }

            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.exiting(cn, mn);
            }
        }
    }

    private final class EntityManagerResourceReference implements ResourceReference<EntityManager> {

        // @GuardedBy("this")
        private EntityManager em;

        private final Future<EntityManagerFactory> emfFuture;

        private final Supplier<EntityManager> emSupplier;

        private EntityManagerResourceReference(final String name,
                                               final SynchronizationType synchronizationType) {
            super();
            final String cn = EntityManagerResourceReference.class.getName();
            final String mn = "<init>";
            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.entering(cn, mn, new Object[] {name, synchronizationType});
            }

            Objects.requireNonNull(name);
            Objects.requireNonNull(synchronizationType);

            // Kick off the lengthy process of setting up an
            // EntityManagerFactory in the background with the
            // optimistic assumption, possibly incorrect, that someone
            // will call getInstance() at some point.
            final ExecutorService taskExecutorService =
                ((WeldManager) CDI.current().getBeanManager()).getServices().get(ExecutorServices.class).getTaskExecutor();
            assert taskExecutorService != null;
            final PersistenceUnitInfo persistenceUnitInfo = getPersistenceUnitInfo(name);
            this.emfFuture =
                taskExecutorService.submit(() -> computeEntityManagerFactory(emfs, persistenceUnitInfo, name));

            if (isResourceLocal(persistenceUnitInfo)) {
                this.emSupplier = () -> {
                    try {
                        return emfFuture.get().createEntityManager();
                    } catch (final ExecutionException executionException) {
                        final Throwable cause = executionException.getCause();
                        assert cause != null;
                        throw new PersistenceException(cause.getMessage(), cause);
                    } catch (final InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(interruptedException.getMessage(), interruptedException);
                    }
                };
            } else {
                assert JTA.equals(persistenceUnitInfo.getTransactionType());
                this.emSupplier = () -> {
                    try {
                        final EntityManager em = emfFuture.get().createEntityManager(synchronizationType);
                        assert em != null;
                        em.setProperty(SynchronizationType.class.getName(), synchronizationType);
                        WeldJpaInjectionServices.this.containerManagedEntityManagers.add(em);
                        return em;
                    } catch (final ExecutionException executionException) {
                        final Throwable cause = executionException.getCause();
                        assert cause != null;
                        throw new PersistenceException(cause.getMessage(), cause);
                    } catch (final InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(interruptedException.getMessage(), interruptedException);
                    }
                };
            }

            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.exiting(cn, mn);
            }
        }

        @Override
        public EntityManager getInstance() {
            final String cn = EntityManagerResourceReference.class.getName();
            final String mn = "getInstance";
            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.entering(cn, mn);
            }

            final EntityManager em;
            synchronized (this) {
                // See
                // https://developer.jboss.org/message/984489#984489;
                // there is no contract governing whether, for
                // example, an EntityManager should be created from
                // within ResourceReference#getInstance() or from
                // within ResourceReferenceFactory#createResource().
                // The maintainers of Weld and CDI suggest following
                // what Wildfly does, as it is most likely (!) to be
                // correct.  So that's what we do.
                //
                // We also ensure that this
                // EntityManagerResourceReference, no matter what,
                // vends a non-null EntityManager whose isOpen()
                // method returns true.
                if (this.em == null || !this.em.isOpen()) {
                    this.em = this.emSupplier.get();
                }
                em = this.em;
            }
            assert em != null;
            assert em.isOpen();

            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.exiting(cn, mn, em);
            }
            return em;
        }

        @Override
        public void release() {
            final String cn = EntityManagerResourceReference.class.getName();
            final String mn = "release";
            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.entering(cn, mn);
            }

            final EntityManager em;
            synchronized (this) {
                em = this.em;
                this.em = null;
            }

            if (em != null) {
                WeldJpaInjectionServices.this.containerManagedEntityManagers.remove(em);
                if (em.isOpen()) {
                    em.close();
                }
            }
            if (!this.emfFuture.isDone()) {
                this.emfFuture.cancel(true);
            }

            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.exiting(cn, mn);
            }
        }

    }

}
