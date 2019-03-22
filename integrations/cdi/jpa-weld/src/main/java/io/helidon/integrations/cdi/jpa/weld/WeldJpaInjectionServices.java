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

import java.lang.annotation.Annotation;
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

import static javax.persistence.spi.PersistenceUnitTransactionType.RESOURCE_LOCAL;

/**
 * A {@link JpaInjectionServices} implementation that integrates JPA
 * functionality into Weld-based CDI environments.
 *
 * @author <a href="mailto:laird.nelson@oracle.com">Laird Nelson</a>
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


    /*
     * Instance fields.
     */


    /**
     * A {@link Logger} for use by this {@link WeldJpaInjectionServices}.
     *
     * <p>This field is never {@code null}.</p>
     */
    private final Logger logger;

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
        this.logger = Logger.getLogger(this.getClass().getName());
        synchronized (WeldJpaInjectionServices.class) {
            // See https://issues.jboss.org/browse/WELD-2563.  Make sure
            // only the first instance is "kept" as it's the one tracked by
            // WeldManager's ServiceRegistry.  The others are discarded.
            if (instance == null) {
                assert !underway;
                instance = this;
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
        final String cn = this.getClass().getName();
        final String mn = "jtaTransactionBegun";
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, mn);
        }

        assert this == instance;
        for (final EntityManager containerManagedEntityManager : this.containerManagedEntityManagers) {
            assert containerManagedEntityManager != null;
            if (logger.isLoggable(Level.FINE)) {
                logger.logp(Level.FINE, cn, mn, "{0} joining transaction", containerManagedEntityManager);
            }
            containerManagedEntityManager.joinTransaction();
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(cn, mn);
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
        final String cn = this.getClass().getName();
        final String mn = "registerPersistenceContextInjectionPoint";
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, mn, injectionPoint);
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
        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(cn, mn, returnValue);
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
     * @param injectionPoint the {@link InjectionPoint} annotated with
     * {@link PersistenceUnit}; must not be {@code null}
     *
     * @return a non-{@code null} {@link ResourceReferenceFactory}
     * whose {@link ResourceReferenceFactory#createResource()} method
     * will create {@link EntityManagerFactory} instances
     *
     * @exception NullPointerException if {@code injectionPoint} is
     * {@code null}
     *
     * @see ResourceReferenceFactory#createResource()
     */
    @Override
    public ResourceReferenceFactory<EntityManagerFactory>
        registerPersistenceUnitInjectionPoint(final InjectionPoint injectionPoint) {
        final String cn = this.getClass().getName();
        final String mn = "registerPersistenceUnitInjectionPoint";
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, mn, injectionPoint);
        }
        underway();
        assert this == instance;
        final ResourceReferenceFactory<EntityManagerFactory> returnValue;
        Objects.requireNonNull(injectionPoint);
        final Annotated annotatedMember = injectionPoint.getAnnotated();
        assert annotatedMember != null;
        final PersistenceUnit persistenceUnitAnnotation = annotatedMember.getAnnotation(PersistenceUnit.class);
        if (persistenceUnitAnnotation == null) {
            throw new IllegalArgumentException("injectionPoint.getAnnotated().getAnnotation(PersistenceUnit.class) == null");
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
        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(cn, mn, returnValue);
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
            // Two, it is forbidden by JPA's contract to call close()
            // on a container-managed EntityManager...which is the
            // only kind of EntityManager placed in this collection.
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
     * @deprecated See the documentation for the {@link
     * JpaInjectionServices#resolvePersistenceContext(InjectionPoint)}
     * method.
     */
    @Deprecated
    @Override
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
     * @deprecated See the documentation for the {@link
     * JpaInjectionServices#resolvePersistenceUnit(InjectionPoint)}
     * method.
     */
    @Deprecated
    @Override
    public EntityManagerFactory resolvePersistenceUnit(final InjectionPoint injectionPoint) {
        return this.registerPersistenceUnitInjectionPoint(injectionPoint).createResource().getInstance();
    }


    /*
     * Static methods.
     */


    private static PersistenceProvider getPersistenceProvider(final PersistenceUnitInfo persistenceUnitInfo) {
        final String providerClassName = Objects.requireNonNull(persistenceUnitInfo).getPersistenceProviderClassName();
        final PersistenceProvider persistenceProvider;
        final CDI<Object> cdi = CDI.current();
        assert cdi != null;
        if (providerClassName == null) {
            persistenceProvider = cdi.select(PersistenceProvider.class).get();
        } else {
            try {
                persistenceProvider =
                    (PersistenceProvider) cdi.select(Class.forName(providerClassName,
                                                                   true,
                                                                   Thread.currentThread().getContextClassLoader())).get();
            } catch (final ReflectiveOperationException exception) {
                throw new PersistenceException(exception.getMessage(), exception);
            }
        }
        return persistenceProvider;
    }

    private static PersistenceUnitInfo getPersistenceUnitInfo(final String name) {
        final String cn = WeldJpaInjectionServices.class.getName();
        final String mn = "getPersistenceUnitInfo";
        final Logger logger = Logger.getLogger(cn);
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, mn, name);
        }

        Objects.requireNonNull(name);
        PersistenceUnitInfo returnValue = null;
        final CDI<Object> cdi = CDI.current();
        if (cdi != null) {
            final BeanManager beanManager = cdi.getBeanManager();
            if (beanManager != null) {
                final Named named = NamedLiteral.of(name);
                assert named != null;
                Set<Bean<?>> beans = beanManager.getBeans(PersistenceUnitInfo.class, named);
                if (beans == null || beans.isEmpty()) {
                    beans = beanManager.getBeans(PersistenceUnitInfo.class, Any.Literal.INSTANCE);
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
                  // We were asked for the persistence unit explicitly
                  // named "fred".  There was no such persistence
                  // unit.  But there is exactly one persistence unit.
                  // Regardless of what its name is, return it.
                  bean = beans.iterator().next();
                  break;
                default:
                  // There are many persistence units.
                  bean = beanManager.resolve(beans);
                  break;
                }
                returnValue = (PersistenceUnitInfo) beanManager.getReference(bean,
                                                                             PersistenceUnitInfo.class,
                                                                             beanManager.createCreationalContext(bean));
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    private static EntityManagerFactory getOrCreateEntityManagerFactory(final Map<String, EntityManagerFactory> emfs,
                                                                        final PersistenceUnitInfo persistenceUnitInfo,
                                                                        final String name) {
        final String cn = WeldJpaInjectionServices.class.getName();
        final String mn = "getOrCreateEntityManagerFactory";
        final Logger logger = Logger.getLogger(cn);
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, mn, new Object[] {emfs, persistenceUnitInfo, name});
        }

        Objects.requireNonNull(emfs);
        Objects.requireNonNull(name);
        final EntityManagerFactory returnValue;
        if (persistenceUnitInfo == null || RESOURCE_LOCAL.equals(persistenceUnitInfo.getTransactionType())) {
            returnValue = emfs.computeIfAbsent(name, n -> Persistence.createEntityManagerFactory(n));
        } else {
            returnValue = emfs.computeIfAbsent(name, n -> createContainerManagedEntityManagerFactory(persistenceUnitInfo));
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    private static EntityManagerFactory createContainerManagedEntityManagerFactory(final PersistenceUnitInfo info) {
        final String cn = WeldJpaInjectionServices.class.getName();
        final String mn = "createContainerManagedEntityManagerFactory";
        final Logger logger = Logger.getLogger(cn);
        if (logger.isLoggable(Level.FINER)) {
            logger.entering(cn, mn, info);
        }

        Objects.requireNonNull(info);
        final PersistenceProvider persistenceProvider = getPersistenceProvider(info);
        assert persistenceProvider != null;
        final CDI<Object> cdi = CDI.current();
        assert cdi != null;
        final BeanManager beanManager = cdi.getBeanManager();
        assert beanManager != null;
        final Map<String, Object> properties = new HashMap<>();
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
                properties.put("javax.validation.ValidatorFactory", beanManager.getReference(vfb, validatorFactoryClass, cc));
            }
        }
        final EntityManagerFactory returnValue = persistenceProvider.createContainerEntityManagerFactory(info, properties);

        if (logger.isLoggable(Level.FINER)) {
            logger.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    private static Bean<?> getValidatorFactoryBean(final BeanManager beanManager,
                                                   final Class<?> validatorFactoryClass) {
        return getValidatorFactoryBean(beanManager, validatorFactoryClass, null);
    }

    private static Bean<?> getValidatorFactoryBean(final BeanManager beanManager,
                                                   final Class<?> validatorFactoryClass,
                                                   final Set<Annotation> qualifiers) {
        Bean<?> returnValue = null;
        if (beanManager != null && validatorFactoryClass != null) {
            final Set<Bean<?>> beans;
            if (qualifiers == null) {
                beans = beanManager.getBeans(validatorFactoryClass);
            } else {
                beans = beanManager.getBeans(validatorFactoryClass, qualifiers.toArray(new Annotation[qualifiers.size()]));
            }
            if (beans != null && !beans.isEmpty()) {
                returnValue = beanManager.resolve(beans);
            }
        }
        return returnValue;
    }


    /*
     * Inner and nested classes.
     */


    private static final class EntityManagerFactoryResourceReference implements ResourceReference<EntityManagerFactory> {

        private final Logger logger;

        private final Map<String, EntityManagerFactory> emfs;

        private final String name;

        private final PersistenceUnitInfo persistenceUnitInfo;

        private EntityManagerFactoryResourceReference(final Map<String, EntityManagerFactory> emfs,
                                                      final String name) {
            super();
            final String cn = this.getClass().getName();
            final String mn = "<init>";
            this.logger = Logger.getLogger(cn);
            if (logger.isLoggable(Level.FINER)) {
                logger.entering(cn, mn, new Object[] {emfs, name});
            }

            this.emfs = Objects.requireNonNull(emfs);
            this.name = Objects.requireNonNull(name);
            this.persistenceUnitInfo = getPersistenceUnitInfo(name);

            if (logger.isLoggable(Level.FINER)) {
                logger.exiting(cn, mn);
            }
        }

        @Override
        public EntityManagerFactory getInstance() {
            final String cn = this.getClass().getName();
            final String mn = "getInstance";
            if (logger.isLoggable(Level.FINER)) {
                logger.entering(cn, mn);
            }

            // See https://developer.jboss.org/message/984489#984489;
            // there is no contract governing whether, for example, an
            // EntityManagerFactory should be created from within
            // ResourceReference#getInstance() or from within
            // ResourceReferenceFactory#createResource().  The
            // maintainers of Weld and CDI suggest following what
            // Wildfly does, as it is most likely (!) to be correct.
            // So that's what we do.
            final EntityManagerFactory returnValue;
            if (this.persistenceUnitInfo == null || RESOURCE_LOCAL.equals(this.persistenceUnitInfo.getTransactionType())) {
                returnValue = getOrCreateEntityManagerFactory(emfs, null, this.name);
            } else {
                returnValue = getOrCreateEntityManagerFactory(emfs, this.persistenceUnitInfo, this.name);
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.exiting(cn, mn, returnValue);
            }
            return returnValue;
        }

        @Override
        public void release() {
            final String cn = this.getClass().getName();
            final String mn = "release";
            if (logger.isLoggable(Level.FINER)) {
                logger.entering(cn, mn);
            }

            final EntityManagerFactory emf = this.emfs.remove(this.name);
            if (emf != null && emf.isOpen()) {
                emf.close();
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.exiting(cn, mn);
            }
        }
    }

    private final class EntityManagerResourceReference implements ResourceReference<EntityManager> {

        private final Logger logger;

        private final String name;

        private final SynchronizationType synchronizationType;

        private final PersistenceUnitInfo persistenceUnitInfo;

        // @GuardedBy("this")
        private EntityManager em;

        private final Future<EntityManagerFactory> emfFuture;

        private final Supplier<EntityManager> emSupplier;

        private EntityManagerResourceReference(final String name,
                                               final SynchronizationType synchronizationType) {
            super();
            final String cn = this.getClass().getName();
            final String mn = "<init>";
            this.logger = Logger.getLogger(cn);
            if (logger.isLoggable(Level.FINER)) {
                logger.entering(cn, mn, new Object[] {name, synchronizationType});
            }

            this.name = Objects.requireNonNull(name);
            this.synchronizationType = Objects.requireNonNull(synchronizationType);
            this.persistenceUnitInfo = getPersistenceUnitInfo(name);
            final ExecutorService taskExecutorService =
                ((WeldManager) CDI.current().getBeanManager()).getServices().get(ExecutorServices.class).getTaskExecutor();
            assert taskExecutorService != null;
            // Kick off the lengthy process of setting up an
            // EntityManagerFactory in the background with the
            // optimistic assumption, possibly incorrect, that someone
            // will call getInstance() at some point.
            this.emfFuture =
                taskExecutorService.submit(() -> getOrCreateEntityManagerFactory(emfs, this.persistenceUnitInfo, this.name));
            if (this.isResourceLocal()) {
                this.emSupplier = () -> {
                    try {
                        return emfFuture.get().createEntityManager();
                    } catch (final ExecutionException executionException) {
                        throw new RuntimeException(executionException.getMessage(), executionException);
                    } catch (final InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(interruptedException.getMessage(), interruptedException);
                    }
                };
            } else {
                this.emSupplier = () -> {
                    try {
                        final EntityManager em = emfFuture.get().createEntityManager(this.synchronizationType);
                        WeldJpaInjectionServices.this.containerManagedEntityManagers.add(em);
                        return em;
                    } catch (final ExecutionException executionException) {
                        throw new RuntimeException(executionException.getMessage(), executionException);
                    } catch (final InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(interruptedException.getMessage(), interruptedException);
                    }
                };
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.exiting(cn, mn);
            }
        }

        private boolean isResourceLocal() {
            return this.persistenceUnitInfo == null || RESOURCE_LOCAL.equals(this.persistenceUnitInfo.getTransactionType());
        }

        @Override
        public EntityManager getInstance() {
            final String cn = this.getClass().getName();
            final String mn = "getInstance";
            if (logger.isLoggable(Level.FINER)) {
                logger.entering(cn, mn);
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
                if (this.em == null) {
                    this.em = this.emSupplier.get();
                }
                em = this.em;
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.exiting(cn, mn, em);
            }
            return em;
        }

        @Override
        public void release() {
            final String cn = this.getClass().getName();
            final String mn = "release";
            if (logger.isLoggable(Level.FINER)) {
                logger.entering(cn, mn);
            }

            final EntityManager em;
            synchronized (this) {
                em = this.em;
                this.em = null;
            }

            if (em != null) {
                WeldJpaInjectionServices.this.containerManagedEntityManagers.remove(em);
                if (em.isOpen() && this.isResourceLocal()) {
                    // Note that according to the javadocs on
                    // EntityManager#close(), you're never supposed to
                    // call EntityManager#close() on a
                    // container-managed EntityManager; hence the
                    // isResourceLocal() check here.
                    em.close();
                }
            }
            if (!this.emfFuture.isDone()) {
                this.emfFuture.cancel(true);
            }

            if (logger.isLoggable(Level.FINER)) {
                logger.exiting(cn, mn);
            }
        }

    }

}
