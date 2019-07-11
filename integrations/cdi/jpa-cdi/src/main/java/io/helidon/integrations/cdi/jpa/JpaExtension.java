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
package io.helidon.integrations.cdi.jpa;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Vetoed;
import javax.enterprise.inject.literal.InjectLiteral;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.enterprise.inject.spi.ProcessProducer;
import javax.enterprise.inject.spi.Producer;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.inject.spi.configurator.BeanConfigurator;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.persistence.Converter;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.MappedSuperclass;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;
import javax.persistence.PersistenceProperty;
import javax.persistence.PersistenceUnit;
import javax.persistence.SynchronizationType;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceProviderResolver;
import javax.persistence.spi.PersistenceProviderResolverHolder;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import io.helidon.integrations.cdi.delegates.DelegatingInjectionTarget;
import io.helidon.integrations.cdi.jpa.PersistenceUnitInfoBean.DataSourceProvider;
import io.helidon.integrations.cdi.jpa.jaxb.Persistence;

import static javax.interceptor.Interceptor.Priority.LIBRARY_AFTER;

/**
 * A {@linkplain Extension portable extension} normally instantiated
 * by the Java {@linkplain java.util.ServiceLoader service provider
 * infrastructure} that integrates the provider-independent parts of
 * <a href="https://javaee.github.io/tutorial/partpersist.html#BNBPY"
 * target="_parent">JPA</a> into CDI.
 *
 * @see PersistenceUnitInfoBean
 */
public class JpaExtension implements Extension {


    /*
     * Static fields.
     */


    /**
     * The {@link Logger} for use by all instances of this class.
     *
     * <p>This field is never {@code null}.</p>
     */
    private static final Logger LOGGER = Logger.getLogger(JpaExtension.class.getName(), "messages");


    /*
     * Instance fields.
     */


    /**
     * A {@link Map} of {@link PersistenceUnitInfoBean} instances that
     * were created by the {@link
     * #gatherImplicitPersistenceUnits(ProcessAnnotatedType,
     * BeanManager)} observer method, indexed by the names of
     * persistence units.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>The contents of this field are used only when no explicit
     * {@link PersistenceUnitInfo} beans are otherwise available in
     * the container.</p>
     *
     * @see #gatherImplicitPersistenceUnits(ProcessAnnotatedType, BeanManager)
     *
     * @see #afterBeanDiscovery(AfterBeanDiscovery, BeanManager)
     */
    private final Map<String, PersistenceUnitInfoBean> implicitPersistenceUnits;

    /**
     * A {@link Map} of {@link Set}s of {@link Class}es whose keys are
     * persistence unit names and whose values are {@link Set}s of
     * {@link Class}es discovered by CDI (and hence consist of
     * unlisted classes in the sense that they might not be found in
     * any {@link PersistenceUnitInfo}).
     *
     * <p>Such {@link Class}es, of course, might not have been weaved
     * appropriately by the relevant {@link PersistenceProvider}.</p>
     *
     * <p>This field is never {@code null}.</p>
     */
    private final Map<String, Set<Class<?>>> unlistedManagedClassesByPersistenceUnitNames;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers annotating CDI
     * injection points related to JPA.
     *
     * <p>This field is never {@code null}.</p>
     */
    private final Set<Set<Annotation>> allQualifiers;

    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JpaExtension}.
     */
    public JpaExtension() {
        super();
        this.unlistedManagedClassesByPersistenceUnitNames = new HashMap<>();
        this.implicitPersistenceUnits = new HashMap<>();
        this.allQualifiers = new HashSet<>();
    }


    /*
     * Instance methods.
     */


    /**
     * Looks for type-level {@link PersistenceContext} annotations
     * that have at least one {@link PersistenceProperty} annotation
     * {@linkplain PersistenceContext#properties() associated with}
     * them and uses them to define persistence units, potentially
     * preventing the need for {@code META-INF/persistence.xml}
     * processing.
     *
     * @param event the {@link ProcessAnnotatedType} event occurring;
     * may be {@code null} in which case no action will be taken
     *
     * @param beanManager the {@link BeanManager} in effect; may be
     * {@code null} in which case no action will be taken
     *
     * @see PersistenceContext
     *
     * @see PersistenceProperty
     *
     * @see PersistenceUnitInfoBean
     */
    private void gatherImplicitPersistenceUnits(@Observes
                                                @WithAnnotations({
                                                    PersistenceContext.class // yes, PersistenceContext, not PersistenceUnit
                                                })
                                                final ProcessAnnotatedType<?> event,
                                                final BeanManager beanManager) {
        final String cn = JpaExtension.class.getName();
        final String mn = "gatherImplicitPersistenceUnits";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, beanManager});
        }

        if (event != null && beanManager != null) {
            final AnnotatedType<?> annotatedType = event.getAnnotatedType();
            if (annotatedType != null
                && !annotatedType.isAnnotationPresent(Vetoed.class)) {
                final Set<? extends PersistenceContext> persistenceContexts =
                    annotatedType.getAnnotations(PersistenceContext.class);
                if (persistenceContexts != null && !persistenceContexts.isEmpty()) {
                    for (final PersistenceContext persistenceContext : persistenceContexts) {
                        assert persistenceContext != null;
                        final PersistenceProperty[] persistenceProperties = persistenceContext.properties();
                        if (persistenceProperties != null && persistenceProperties.length > 0) {
                            final String persistenceUnitName = persistenceContext.unitName();
                            assert persistenceUnitName != null;
                            PersistenceUnitInfoBean persistenceUnit = this.implicitPersistenceUnits.get(persistenceUnitName);
                            if (persistenceUnit == null) {
                                final String jtaDataSourceName;
                                if (persistenceUnitName.isEmpty()) {
                                    jtaDataSourceName = null;
                                } else {
                                    jtaDataSourceName = persistenceUnitName;
                                }
                                final Class<?> javaClass = annotatedType.getJavaClass();
                                assert javaClass != null;
                                URL persistenceUnitRoot = null;
                                final ProtectionDomain pd = javaClass.getProtectionDomain();
                                if (pd != null) {
                                    final CodeSource cs = pd.getCodeSource();
                                    if (cs != null) {
                                        persistenceUnitRoot = cs.getLocation();
                                    }
                                }
                                final Properties properties = new Properties();
                                for (final PersistenceProperty persistenceProperty : persistenceProperties) {
                                    assert persistenceProperty != null;
                                    final String persistencePropertyName = persistenceProperty.name();
                                    assert persistencePropertyName != null;
                                    if (!persistencePropertyName.isEmpty()) {
                                        properties.setProperty(persistencePropertyName, persistenceProperty.value());
                                    }
                                }
                                persistenceUnit =
                                    new PersistenceUnitInfoBean(persistenceUnitName,
                                                                persistenceUnitRoot,
                                                                null,
                                                                new BeanManagerBackedDataSourceProvider(beanManager),
                                                                properties);
                                this.implicitPersistenceUnits.put(persistenceUnitName, persistenceUnit);
                            }
                        }
                    }
                }
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Tracks {@linkplain Converter converters}, {@linkplain Entity
     * entities}, {@linkplain Embeddable embeddables} and {@linkplain
     * MappedSuperclass mapped superclasses} that were auto-discovered
     * by CDI bean discovery, and makes sure that they are not
     * actually CDI beans, since according to the JPA specification
     * they cannot be.
     *
     * <p>This method also keeps track of these classes as potential
     * "unlisted classes" to be used by a {@linkplain
     * PersistenceUnitInfo persistence unit} if its {@linkplain
     * PersistenceUnitInfo#excludeUnlistedClasses()} method returns
     * {@code false}.</p>
     *
     * @param event the event describing the {@link AnnotatedType}
     * being processed; may be {@code null} in which case no action
     * will be taken
     *
     * @see Converter
     *
     * @see Embeddable
     *
     * @see Entity
     *
     * @see MappedSuperclass
     *
     * @see PersistenceUnitInfo#excludeUnlistedClasses()
     */
    private void discoverManagedClasses(@Observes
                                        @WithAnnotations({
                                            Converter.class,
                                            Embeddable.class,
                                            Entity.class,
                                            MappedSuperclass.class
                                        })
                                        final ProcessAnnotatedType<?> event) {
        final String cn = JpaExtension.class.getName();
        final String mn = "discoverManagedClasses";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, event);
        }

        if (event != null) {
            final AnnotatedType<?> annotatedType = event.getAnnotatedType();
            if (annotatedType != null && !annotatedType.isAnnotationPresent(Vetoed.class)) {
                this.assignManagedClassToPersistenceUnit(annotatedType.getAnnotations(PersistenceContext.class),
                                                         annotatedType.getAnnotations(PersistenceUnit.class),
                                                         annotatedType.getJavaClass());
                event.veto(); // managed classes can't be beans
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Given {@link Set}s of {@link PersistenceContext} and {@link
     * PersistenceUnit} annotations that will be used for their {@code
     * unitName} elements only, associates the supplied {@link Class}
     * with the persistence units implied by the annotations.
     *
     * @param persistenceContexts a {@link Set} of {@link
     * PersistenceContext}s whose {@link PersistenceContext#unitName()
     * unitName} elements identify persistence units; may be {@code
     * null} or {@linkplain Collection#isEmpty() empty}
     *
     * @param persistenceUnits a {@link Set} of {@link
     * PersistenceUnit}s whose {@link PersistenceUnit#unitName()
     * unitName} elements identify persistence units; may be {@code
     * null} or {@linkplain Collection#isEmpty() empty}
     *
     * @param c the {@link Class} to associate; may be {@code null} in
     * which case no action will be taken
     *
     * @see PersistenceContext
     *
     * @see PersistenceUnit
     */
    private void assignManagedClassToPersistenceUnit(final Set<? extends PersistenceContext> persistenceContexts,
                                                     final Set<? extends PersistenceUnit> persistenceUnits,
                                                     final Class<?> c) {
        final String cn = JpaExtension.class.getName();
        final String mn = "assignManagedClassToPersistenceUnit";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {persistenceContexts, persistenceUnits, c});
        }

        if (c != null) {
            boolean processed = false;
            if (persistenceContexts != null && !persistenceContexts.isEmpty()) {
                for (final PersistenceContext persistenceContext : persistenceContexts) {
                    if (persistenceContext != null) {
                        final String name = persistenceContext.unitName(); // yes, unitName
                        assert name != null;
                        if (!name.isEmpty()) {
                            processed = true;
                            addUnlistedManagedClass(name, c);
                        }
                    }
                }
            }
            if (persistenceUnits != null && !persistenceUnits.isEmpty()) {
                for (final PersistenceUnit persistenceUnit : persistenceUnits) {
                    if (persistenceUnit != null) {
                        final String name = persistenceUnit.unitName(); // yes, unitName
                        assert name != null;
                        if (!name.isEmpty()) {
                            processed = true;
                            addUnlistedManagedClass(name, c);
                        }
                    }
                }
            }
            if (!processed) {
                addUnlistedManagedClass("", c);
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Given a {@link Class} and a name of a persistence unit,
     * associates the {@link Class} with that persistence unit as a
     * member of its list of governed classes.
     *
     * @param name the name of the persistence unit in question; may
     * be {@code null} in which case the empty string ({@code ""})
     * will be used instead
     *
     * @param managedClass the {@link Class} to associate; may be
     * {@code null} in which case no action will be taken
     *
     * @see PersistenceUnitInfo#getManagedClassNames()
     */
    private void addUnlistedManagedClass(String name, final Class<?> managedClass) {
        final String cn = JpaExtension.class.getName();
        final String mn = "addUnlistedManagedClass";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {name, managedClass});
        }

        if (managedClass != null) {
            if (name == null) {
                name = "";
            }
            if (!name.isEmpty()) {
                Set<Class<?>> unlistedManagedClasses = this.unlistedManagedClassesByPersistenceUnitNames.get(name);
                if (unlistedManagedClasses == null) {
                    unlistedManagedClasses = new HashSet<>();
                    this.unlistedManagedClassesByPersistenceUnitNames.put(name, unlistedManagedClasses);
                }
                unlistedManagedClasses.add(managedClass);
            }
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Converts {@code META-INF/persistence.xml} resources into {@link
     * PersistenceUnitInfo} objects and takes into account any other
     * {@link PersistenceUnitInfo} objects that already exist and
     * ensures that all of them are registered as CDI beans.
     *
     * <p>This allows other CDI-provider-specific mechanisms to use
     * these {@link PersistenceUnitInfo} beans as inputs for creating
     * {@link EntityManager} instances.</p>
     *
     * @param event the {@link AfterBeanDiscovery} event describing
     * the fact that bean discovery has been performed; must not be
     * {@code null}
     *
     * @param beanManager the {@link BeanManager} currently in effect;
     * must not be {@code null}
     *
     * @exception IOException if an input or output error occurs,
     * typically because a {@code META-INF/persistence.xml} resource
     * was found but could not be loaded for some reason
     *
     * @exception JAXBException if there was a problem {@linkplain
     * Unmarshaller#unmarshal(Reader) unmarshalling} a {@code
     * META-INF/persistence.xml} resource
     *
     * @exception NullPointerException if either {@code event} or
     * {@code beanManager} is {@code null}
     *
     * @exception ReflectiveOperationException if reflection failed
     *
     * @exception XMLStreamException if there was a problem setting up
     * JAXB
     *
     * @see PersistenceUnitInfo
     */
    private void addPersistenceUnitInfoBeans(@Observes
                                             @Priority(LIBRARY_AFTER)
                                             final AfterBeanDiscovery event,
                                             final BeanManager beanManager)
        throws IOException, JAXBException, ReflectiveOperationException, XMLStreamException {
        final String cn = JpaExtension.class.getName();
        final String mn = "addPersistenceUnitInfoBeans";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, beanManager});
        }

        Objects.requireNonNull(event);
        Objects.requireNonNull(beanManager);

        final Collection<? extends PersistenceProvider> providers = addPersistenceProviderBeans(event);

        // Should we consider type-level @PersistenceContext
        // definitions of persistence units ("implicits")?
        boolean processImplicits = true;

        // Collect all pre-existing PersistenceUnitInfo beans
        // (i.e. supplied by the end user) and make sure their
        // associated PersistenceProviders are beanified.  (Almost
        // always this Set will be empty.)
        final Set<Bean<?>> preexistingPersistenceUnitInfoBeans =
            beanManager.getBeans(PersistenceUnitInfo.class, Any.Literal.INSTANCE);
        if (preexistingPersistenceUnitInfoBeans != null && !preexistingPersistenceUnitInfoBeans.isEmpty()) {
            processImplicits = false;
            maybeAddPersistenceProviderBeans(event, beanManager, preexistingPersistenceUnitInfoBeans, providers);
        }

        // Next, and most commonly, load all META-INF/persistence.xml
        // resources with JAXB, and turn them into PersistenceUnitInfo
        // instances, and add beans for all of them as well as their
        // associated PersistenceProviders (if applicable).
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        final Enumeration<URL> urls = classLoader.getResources("META-INF/persistence.xml");
        if (urls != null && urls.hasMoreElements()) {
            processImplicits = false;
            this.processPersistenceXmls(event, beanManager, classLoader, urls, providers);
        }

        // If we did not find any PersistenceUnitInfo instances via
        // any other means, only then look at those defined "implicitly",
        // i.e. via type-level @PersistenceContext annotations.
        if (processImplicits) {
            this.processImplicitPersistenceUnits(event, providers);
        }

        this.unlistedManagedClassesByPersistenceUnitNames.clear();
        this.implicitPersistenceUnits.clear();

        // Now add synthetic beans to support any true CDI injection
        // points that reference EntityManagers.  Note that by
        // default @PersistenceContext-annotated fields are not "true"
        // CDI injection points.
        this.addEntityManagerBeans(event);

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Converts something like {@code @PersistenceContext(unitName =
     * "fred") private EntityManager em;} to
     * {@code @Inject @Named("fred") @Synchronized @JPATransactionScoped
     * private EntityManager em;}.
     */
    private <T> void processTypesWithPersistenceContextAnnotations(@Observes
                                                                   @WithAnnotations(PersistenceContext.class)
                                                                   final ProcessAnnotatedType<T> event) {

        event.configureAnnotatedType()
            .filterFields(f -> f.isAnnotationPresent(PersistenceContext.class)
                          && !f.isAnnotationPresent(Inject.class))
            .forEach(fc -> {
                    final PersistenceContext pc = fc.getAnnotated().getAnnotation(PersistenceContext.class);
                    assert pc != null;
                    fc.remove(a -> a == pc);
                    fc.add(InjectLiteral.INSTANCE);
                    fc.add(ContainerManaged.Literal.INSTANCE);
                    if (PersistenceContextType.EXTENDED.equals(pc.type())) {
                        fc.add(Extended.Literal.INSTANCE);
                    } else {
                        fc.add(JPATransactionScoped.Literal.INSTANCE);
                    }
                    if (SynchronizationType.UNSYNCHRONIZED.equals(pc.synchronization())) {
                        fc.add(Unsynchronized.Literal.INSTANCE);
                    } else {
                        fc.add(Synchronized.Literal.INSTANCE);
                    }
                    fc.add(NamedLiteral.of(pc.unitName().trim()));
                });
    }

    private <R> void installSpecialProducer(@Observes final ProcessProducer<?, R> event) {
        final Producer<R> producer = event.getProducer();
        final Collection<?> keys = getKeys(producer);
        if (keys != null && !keys.isEmpty()) {
            event.setProducer(new EntityManagerReferencingProducer<>(producer, keys));
        }
    }

    private <T> void installSpecialInjectionTarget(@Observes final ProcessInjectionTarget<T> event) {
        final InjectionTarget<T> injectionTarget = event.getInjectionTarget();
        final Collection<?> keys = getKeys(injectionTarget);
        if (keys != null && !keys.isEmpty()) {
            event.setInjectionTarget(new DelegatingInjectionTarget<>(injectionTarget,
                                                                     new EntityManagerReferencingProducer<>(injectionTarget,
                                                                                                            keys)));
        }
    }

    private static Collection<?> getKeys(final Producer<?> producer) {
        final Set<InjectionPoint> injectionPoints = producer.getInjectionPoints();
        final Set<Object> keys = new HashSet<>();
        for (final InjectionPoint injectionPoint : injectionPoints) {
            final Type type = injectionPoint.getType();
            if (type instanceof Class && EntityManager.class.isAssignableFrom((Class<?>) type)) {
                final Set<Annotation> qualifiers = injectionPoint.getQualifiers();
                if (qualifiers != null && !qualifiers.isEmpty()) {
                    boolean jpaTransactionScoped = false;
                    Named named = null;
                    for (final Annotation qualifier : qualifiers) {
                        if (qualifier instanceof Named) {
                            if (named == null) {
                                named = (Named) qualifier;
                            }
                        } else if (qualifier instanceof JPATransactionScoped) {
                            if (!jpaTransactionScoped) {
                                jpaTransactionScoped = true;
                            }
                        }
                    }
                    if (jpaTransactionScoped && named != null) {
                        // Because of processing the annotated type stuff
                        // above, we have converted all PersistenceContext
                        // annotations into sequences like:
                        //   @Inject
                        //   @ContainerManaged
                        //   @Named("fred")
                        //   @JPATransactionScoped
                        //   @Synchronized
                        // ...so we look for @Named here.
                        keys.add(named.value().trim());
                    }
                }
            }
        }
        return keys;
    }

    private <T, X extends EntityManager> void processEntityManagerInjectionPoint(@Observes
                                                                                 final ProcessInjectionPoint<T, X> event) {
        this.allQualifiers.add(event.getInjectionPoint().getQualifiers());
    }

    private void addEntityManagerBeans(final AfterBeanDiscovery event) {
        for (final Set<Annotation> qualifiers : this.allQualifiers) {
            addContainerManagedEntityManagerFactory(event.addBean(), qualifiers);
            addCDITransactionScopedEntityManager(event.addBean(), qualifiers);
            addJPATransactionScopedEntityManager(event.addBean(), qualifiers);
        }
    }

    // Revisit: non-private for the moment only
    static void addContainerManagedEntityManagerFactory(final BeanConfigurator<EntityManagerFactory> beanConfigurator,
                                                        final Set<Annotation> suppliedQualifiers) {
        Objects.requireNonNull(beanConfigurator);
        Objects.requireNonNull(suppliedQualifiers);

        final Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.add(ContainerManaged.Literal.INSTANCE);

        beanConfigurator.addType(EntityManagerFactory.class)
            .scope(ApplicationScoped.class)
            .addQualifiers(qualifiers)
            .produceWith(instance -> produceContainerManagedEntityManagerFactory(instance, qualifiers))
            .disposeWith((emf, instance) -> emf.close());
    }

    // Revisit: non-private for the moment only
    static void addCDITransactionScopedEntityManager(final BeanConfigurator<EntityManager> beanConfigurator,
                                                     final Set<Annotation> suppliedQualifiers) {
        Objects.requireNonNull(beanConfigurator);
        Objects.requireNonNull(suppliedQualifiers);

        final Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.remove(ContainerManaged.Literal.INSTANCE);
        qualifiers.add(CDITransactionScoped.Literal.INSTANCE);

        // Adds an EntityManager that is not wrapped in any way and is in
        // CDI's TransactionScoped scope.  Used as a delegate only.

        beanConfigurator.addType(EntityManager.class)
            .scope(javax.transaction.TransactionScoped.class)
            .addQualifiers(qualifiers)
            .produceWith(instance -> {
                final Set<Annotation> emfQualifiers = new HashSet<>(qualifiers);
                emfQualifiers.remove(CDITransactionScoped.Literal.INSTANCE);
                emfQualifiers.add(ContainerManaged.Literal.INSTANCE);
                final EntityManagerFactory emf =
                    instance.select(EntityManagerFactory.class,
                                    emfQualifiers.toArray(new Annotation[emfQualifiers.size()])).get();
                // Revisit: get properties and pass them too
                if (emfQualifiers.contains(Unsynchronized.Literal.INSTANCE)) {
                    return emf.createEntityManager(SynchronizationType.UNSYNCHRONIZED);
                } else {
                    return emf.createEntityManager(SynchronizationType.SYNCHRONIZED);
                }
            })
            .disposeWith((em, instance) -> {
                    em.close();
                });
    }

    // Revisit: non-private for the moment only
    static boolean inTransaction(final Instance<? extends Transaction> instanceTransaction) throws SystemException {
        final boolean returnValue;
        if (instanceTransaction == null || instanceTransaction.isUnsatisfied()) {
            returnValue = false;
        } else {
            final Transaction transaction = instanceTransaction.get();
            returnValue = transaction != null && transaction.getStatus() == Status.STATUS_ACTIVE;
        }
        return returnValue;
    }

    // Revisit: non-private for the moment only
    static void addJPATransactionScopedEntityManager(final BeanConfigurator<EntityManager> beanConfigurator,
                                                             final Set<Annotation> suppliedQualifiers) {
        Objects.requireNonNull(beanConfigurator);
        Objects.requireNonNull(suppliedQualifiers);

        beanConfigurator.addType(EntityManager.class)
            .scope(Dependent.class)
            .addQualifiers(suppliedQualifiers)
            .addQualifiers(ContainerManaged.Literal.INSTANCE)
            .addQualifiers(JPATransactionScoped.Literal.INSTANCE)
            .produceWith(instance -> {

                // Get an Instance that can give us a @Default
                // Transaction.  We deliberately do not use
                // qualifiers.  It will be in TransactionScoped scope.
                final Instance<Transaction> instanceTransaction = instance.select(Transaction.class);

                // Get an Instance that can give us
                // a @CDITransactionScoped @ContainerManaged
                // EntityManager.  It will be in
                // JTA's @TransactionScoped CDI scope.  We don't need
                // (or want) @Any or @JPATransactionScoped in our
                // selection criteria.
                final Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
                qualifiers.remove(Any.Literal.INSTANCE);
                qualifiers.remove(JPATransactionScoped.Literal.INSTANCE);
                qualifiers.add(ContainerManaged.Literal.INSTANCE);
                qualifiers.add(CDITransactionScoped.Literal.INSTANCE);
                final Instance<EntityManager> instanceCdiTransactionScopedEntityManager =
                    instance.select(EntityManager.class, qualifiers.toArray(new Annotation[qualifiers.size()]));

                // Get an Instance that can give us
                // a @ContainerManaged EntityManagerFactory.  We don't
                // want @Any, @JPATransactionScoped
                // or @CDITransactionScoped in our selection criteria.
                // The EntityManagerFactory will be
                // in @ApplicationScoped scope, or it better be,
                // anyway.
                qualifiers.remove(CDITransactionScoped.Literal.INSTANCE);
                final Instance<EntityManagerFactory> instanceEmf =
                    instance.select(EntityManagerFactory.class, qualifiers.toArray(new Annotation[qualifiers.size()]));

                // Is this synchronized or not?
                final SynchronizationType synchronizationType;
                if (qualifiers.contains(Unsynchronized.Literal.INSTANCE)) {
                    synchronizationType = SynchronizationType.UNSYNCHRONIZED;
                } else {
                    synchronizationType = SynchronizationType.SYNCHRONIZED;
                }

                // Create the actual EntityManager that will be
                // produced.  It itself will be in @Dependent scope
                // but its delegate may be something else.
                return new DelegatingEntityManager() {
                    @Override
                    protected EntityManager acquireDelegate() {
                        final EntityManager returnValue;
                        boolean inTransaction;
                        try {
                            inTransaction = JpaExtension.inTransaction(instanceTransaction);
                        } catch (final SystemException systemException) {
                            throw new CreationException(systemException.getMessage(), systemException);
                        }
                        if (inTransaction) {
                            returnValue = instanceCdiTransactionScopedEntityManager.get();
                        } else {
                            returnValue =
                                new NonTransactionalTransactionScopedEntityManager(instanceEmf.get()
                                                                                   .createEntityManager(synchronizationType),
                                                                                   this);
                        }
                        return returnValue;
                    }

                    @Override
                    public void close() {
                        // Revisit: Wildfly allows end users to close
                        // UNSYNCHRONIZED container-managed
                        // EntityManagers:
                        // https://github.com/wildfly/wildfly/blob/7f80f0150297bbc418a38e7e23da7cf0431f7c28/jpa/subsystem/src/main/java/org/jboss/as/jpa/container/UnsynchronizedEntityManagerWrapper.java#L75-L78
                        // I don't know why.  Glassfish does not:
                        // https://github.com/javaee/glassfish/blob/f9e1f6361dcc7998cacccb574feef5b70bf84e23/appserver/common/container-common/src/main/java/com/sun/enterprise/container/common/impl/EntityManagerWrapper.java#L752-L761
                        throw new IllegalStateException();
                    }
                };
            }); // deliberately no disposeWith()
    }

    private static Collection<? extends PersistenceProvider> addPersistenceProviderBeans(final AfterBeanDiscovery event) {
        final String cn = JpaExtension.class.getName();
        final String mn = "addPersistenceProviderBeans";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, event);
        }

        Objects.requireNonNull(event);
        final PersistenceProviderResolver resolver = PersistenceProviderResolverHolder.getPersistenceProviderResolver();
        event.addBean()
            .types(PersistenceProviderResolver.class)
            .scope(Singleton.class)
            .createWith(cc -> resolver);
        final Collection<? extends PersistenceProvider> providers = resolver.getPersistenceProviders();
        assert providers != null;
        for (final PersistenceProvider provider : providers) {
            event.addBean()
                .addTransitiveTypeClosure(provider.getClass())
                .scope(Singleton.class)
                .createWith(cc -> provider);
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, providers);
        }
        return providers;
    }

    private void processImplicitPersistenceUnits(final AfterBeanDiscovery event,
                                                 final Collection<? extends PersistenceProvider> providers)
        throws ReflectiveOperationException {
        final String cn = JpaExtension.class.getName();
        final String mn = "processImplicitPersistenceUnits";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, providers});
        }

        Objects.requireNonNull(event);
        for (final PersistenceUnitInfoBean persistenceUnitInfoBean : this.implicitPersistenceUnits.values()) {
            assert persistenceUnitInfoBean != null;
            final String persistenceUnitName = persistenceUnitInfoBean.getPersistenceUnitName();
            if (!persistenceUnitInfoBean.excludeUnlistedClasses()) {
                final Collection<? extends Class<?>> unlistedManagedClasses =
                    this.unlistedManagedClassesByPersistenceUnitNames.get(persistenceUnitName);
                if (unlistedManagedClasses != null && !unlistedManagedClasses.isEmpty()) {
                    for (final Class<?> unlistedManagedClass : unlistedManagedClasses) {
                        assert unlistedManagedClass != null;
                        persistenceUnitInfoBean.addManagedClassName(unlistedManagedClass.getName());
                    }
                }
            }
            event.addBean()
                .types(Collections.singleton(PersistenceUnitInfo.class))
                .scope(Singleton.class)
                .addQualifiers(NamedLiteral.of(persistenceUnitName == null ? "" : persistenceUnitName))
                .createWith(cc -> persistenceUnitInfoBean);
            maybeAddPersistenceProviderBean(event, persistenceUnitInfoBean, providers);
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private void processPersistenceXmls(final AfterBeanDiscovery event,
                                        final BeanManager beanManager,
                                        final ClassLoader classLoader,
                                        final Enumeration<URL> urls,
                                        final Collection<? extends PersistenceProvider> providers)
        throws IOException, JAXBException, ReflectiveOperationException, XMLStreamException {
        final String cn = JpaExtension.class.getName();
        final String mn = "processPersistenceXmls";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, beanManager, classLoader, urls, providers});
        }

        Objects.requireNonNull(event);
        if (urls != null && urls.hasMoreElements()) {
            final Supplier<? extends ClassLoader> tempClassLoaderSupplier;
            if (classLoader instanceof URLClassLoader) {
                tempClassLoaderSupplier = () -> new URLClassLoader(((URLClassLoader) classLoader).getURLs());
            } else {
                tempClassLoaderSupplier = () -> classLoader;
            }
            // We use StAX for XML loading because it is the same
            // strategy used by CDI implementations.  If the end user
            // wants to customize the StAX implementation then we want
            // that customization to apply here as well.
            //
            // Note that XMLInputFactory is NOT deprecated in JDK 8:
            //   https://docs.oracle.com/javase/8/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory--
            // ...but IS deprecated in JDK 9:
            //   https://docs.oracle.com/javase/9/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory--
            // ...with an incorrect claim that it was deprecated since
            // JDK 1.7.  In JDK 7 it actually was *not* deprecated:
            //   https://docs.oracle.com/javase/7/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory()
            // ...and now in JDK 10 it is NO LONGER deprecated:
            //   https://docs.oracle.com/javase/10/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory()
            // ...nor in JDK 11:
            //   https://docs.oracle.com/en/java/javase/11/docs/api/java.xml/javax/xml/stream/XMLInputFactory.html#newFactory()
            // ...nor in JDK 12:
            //   https://docs.oracle.com/en/java/javase/12/docs/api/java.xml/javax/xml/stream/XMLInputFactory.html#newFactory()
            // So we suppress deprecation warnings since deprecation
            // in JDK 9 appears to have been a mistake.
            @SuppressWarnings("deprecation")
            final XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
            assert xmlInputFactory != null;

            // See
            // https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.md#xmlinputfactory-a-stax-parser
            xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            xmlInputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);

            final Unmarshaller unmarshaller =
                JAXBContext.newInstance(Persistence.class.getPackage().getName()).createUnmarshaller();
            assert unmarshaller != null;
            final DataSourceProvider dataSourceProvider = new BeanManagerBackedDataSourceProvider(beanManager);
            while (urls.hasMoreElements()) {
                final URL url = urls.nextElement();
                assert url != null;
                Collection<? extends PersistenceUnitInfo> persistenceUnitInfos = null;
                try (InputStream inputStream = new BufferedInputStream(url.openStream())) {
                    final XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(inputStream);
                    assert reader != null;
                    persistenceUnitInfos =
                        PersistenceUnitInfoBean.fromPersistence((Persistence) unmarshaller.unmarshal(reader),
                                                                classLoader,
                                                                tempClassLoaderSupplier,
                                                                new URL(url, ".."), // e.g. META-INF/..
                                                                this.unlistedManagedClassesByPersistenceUnitNames,
                                                                dataSourceProvider);
                }
                if (persistenceUnitInfos != null && !persistenceUnitInfos.isEmpty()) {
                    for (final PersistenceUnitInfo persistenceUnitInfo : persistenceUnitInfos) {
                        final String persistenceUnitName = persistenceUnitInfo.getPersistenceUnitName();
                        event.addBean()
                            .types(Collections.singleton(PersistenceUnitInfo.class))
                            .scope(Singleton.class)
                            .addQualifiers(NamedLiteral.of(persistenceUnitName == null ? "" : persistenceUnitName))
                            .createWith(cc -> persistenceUnitInfo);
                        maybeAddPersistenceProviderBean(event, persistenceUnitInfo, providers);
                    }
                }
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private static void maybeAddPersistenceProviderBeans(final AfterBeanDiscovery event,
                                                         final BeanManager beanManager,
                                                         final Set<Bean<?>> preexistingPersistenceUnitInfoBeans,
                                                         final Collection<? extends PersistenceProvider> providers)
        throws ReflectiveOperationException {
        final String cn = JpaExtension.class.getName();
        final String mn = "maybeAddPersistenceProviderBeans";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, beanManager, preexistingPersistenceUnitInfoBeans, providers});
        }

        Objects.requireNonNull(event);
        Objects.requireNonNull(beanManager);
        Objects.requireNonNull(preexistingPersistenceUnitInfoBeans);
        for (final Bean<?> bean : preexistingPersistenceUnitInfoBeans) {
            if (bean != null) {
                assert bean.getTypes().contains(PersistenceUnitInfo.class);
                @SuppressWarnings("unchecked")
                final Bean<PersistenceUnitInfo> preexistingPersistenceUnitInfoBean = (Bean<PersistenceUnitInfo>) bean;
                // We use Contextual#create() directly to create a
                // PersistenceUnitInfo contextual instance (normally
                // for this use case in CDI you would acquire a
                // contextual reference via
                // BeanManager#getReference(), but it is too early in
                // the (spec-defined) lifecycle to do that here).  We
                // also deliberately do not use
                // Context#get(Contextual, CreationalContext), since
                // that might "install" the instance so acquired in
                // whatever Context/scope it is defined in and we just
                // need it transiently.
                //
                // Getting a contextual instance this way, via
                // Contextual#create(), is normally frowned upon,
                // since it bypasses CDI's Context mechansims and
                // proxying and interception features (it is the
                // foundation upon which they are built), but here we
                // need the instance only for the return values of
                // getPersistenceProviderClassName() and
                // getClassLoader().  We then destroy the instance
                // immediately so that everything behaves as though
                // this contextual instance acquired by shady means
                // never existed.
                final CreationalContext<PersistenceUnitInfo> cc =
                    beanManager.createCreationalContext(preexistingPersistenceUnitInfoBean);
                final PersistenceUnitInfo pui = preexistingPersistenceUnitInfoBean.create(cc);
                assert pui != null;
                try {
                    maybeAddPersistenceProviderBean(event, pui, providers);
                } finally {
                    preexistingPersistenceUnitInfoBean.destroy(pui, cc);
                    // Contextual#destroy() *should* release the
                    // CreationalContext, but it is an idempotent call
                    // and many Bean authors forget to do this.
                    cc.release();
                }
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Given a {@link PersistenceUnitInfo} and a {@link Collection} of
     * {@link PersistenceProvider} instances representing already
     * "beanified" {@link PersistenceProvider}s, adds a CDI bean for
     * the {@linkplain
     * PersistenceUnitInfo#getPersistenceProviderClassName()
     * persistence provider referenced by the supplied
     * <code>PersistenceUnitInfo</code>} if appropriate.
     *
     * @param event the {@link AfterBeanDiscovery} event that will do
     * the actual bean addition; must not be {@code null}
     *
     * @param persistenceUnitInfo the {@link PersistenceUnitInfo}
     * whose {@linkplain
     * PersistenceUnitInfo#getPersistenceProviderClassName()
     * associated persistence provider} will be beanified; must not be
     * {@code null}
     *
     * @param providers a {@link Collection} of {@link
     * PersistenceProvider} instances that represent {@link
     * PersistenceProvider}s that have already had beans added for
     * them; may be {@code null}
     *
     * @exception NullPointerException if {@code event} or {@code
     * persistenceUnitInfo} is {@code null}
     *
     * @exception ReflectiveOperationException if an error occurs
     * during reflection
     */
    private static void maybeAddPersistenceProviderBean(final AfterBeanDiscovery event,
                                                        final PersistenceUnitInfo persistenceUnitInfo,
                                                        final Collection<? extends PersistenceProvider> providers)
        throws ReflectiveOperationException {
        final String cn = JpaExtension.class.getName();
        final String mn = "maybeAddPersistenceProviderBean";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, persistenceUnitInfo, providers});
        }

        Objects.requireNonNull(event);
        Objects.requireNonNull(persistenceUnitInfo);
        final String providerClassName = persistenceUnitInfo.getPersistenceProviderClassName();
        if (providerClassName != null) {

            boolean add = true;

            if (providers != null && !providers.isEmpty()) {
                for (final PersistenceProvider provider : providers) {
                    if (provider != null && provider.getClass().getName().equals(providerClassName)) {
                        add = false;
                        break;
                    }
                }
            }

            if (add) {
                // The PersistenceProvider class in question is not one we
                // already loaded.  Add a bean for it too.

                final String persistenceUnitName = persistenceUnitInfo.getPersistenceUnitName();
                event.addBean()
                    .types(PersistenceProvider.class)
                    .scope(Singleton.class)
                    .addQualifiers(NamedLiteral.of(persistenceUnitName == null ? "" : persistenceUnitName))
                    .createWith(cc -> {
                            try {
                                ClassLoader classLoader = persistenceUnitInfo.getClassLoader();
                                if (classLoader == null) {
                                    classLoader = Thread.currentThread().getContextClassLoader();
                                }
                                assert classLoader != null;
                                @SuppressWarnings("unchecked")
                                final Class<? extends PersistenceProvider> c =
                                    (Class<? extends PersistenceProvider>) Class.forName(providerClassName, true, classLoader);
                                return c.getDeclaredConstructor().newInstance();
                            } catch (final ReflectiveOperationException reflectiveOperationException) {
                                throw new CreationException(reflectiveOperationException.getMessage(),
                                                            reflectiveOperationException);
                            }
                        });
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    // Revisit: non-private for the moment only
    static EntityManagerFactory produceContainerManagedEntityManagerFactory(final Instance<Object> instance,
                                                                            final Set<Annotation> qualifiers) {
        final String cn = JpaExtension.class.getName();
        final String mn = "produceContainerManagedEntityManagerFactory";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {instance, qualifiers});
        }
        Objects.requireNonNull(instance);
        Objects.requireNonNull(qualifiers);

        final Set<Annotation> puQualifiers = new HashSet<>(qualifiers);
        puQualifiers.remove(ContainerManaged.Literal.INSTANCE);
        final PersistenceUnitInfo pu = getPersistenceUnitInfo(instance, puQualifiers);
        assert pu != null;
        if (PersistenceUnitTransactionType.RESOURCE_LOCAL.equals(pu.getTransactionType())) {
            throw new CreationException();
        }
        PersistenceProvider persistenceProvider = null;
        try {
            persistenceProvider = getPersistenceProvider(instance, puQualifiers, pu);
        } catch (final ReflectiveOperationException reflectiveOperationException) {
            throw new CreationException(reflectiveOperationException.getMessage(),
                                        reflectiveOperationException);
        }
        assert persistenceProvider != null;
        final Map<String, Object> properties = new HashMap<>();
        properties.put("javax.persistence.bean.manager", instance.select(BeanManager.class).get());

        // Revisit: deal with validator stuff; see jpa-weld
        final EntityManagerFactory returnValue = persistenceProvider.createContainerEntityManagerFactory(pu, properties);
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    // Revisit: non-private for the moment only
    static PersistenceUnitInfo getPersistenceUnitInfo(final Instance<Object> instance,
                                                      final Set<Annotation> suppliedQualifiers) {
        final String cn = JpaExtension.class.getName();
        final String mn = "getPersistenceUnitInfo";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {instance, suppliedQualifiers});
        }
        Objects.requireNonNull(instance);
        Objects.requireNonNull(suppliedQualifiers);

        final Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        Instance<PersistenceUnitInfo> puInstance = instance.select(PersistenceUnitInfo.class,
                                                                   qualifiers.toArray(new Annotation[qualifiers.size()]));
        assert puInstance != null;
        if (puInstance.isUnsatisfied()) {
            // We didn't find any PersistenceUnitInfo named, for
            // example, "fred".  So let's remove Named qualifiers and
            // see what we get.
            qualifiers.removeIf(q -> q instanceof Named);
            puInstance = instance.select(PersistenceUnitInfo.class,
                                         qualifiers.toArray(new Annotation[qualifiers.size()]));
            assert puInstance != null;
            // Revisit: suppose someone has asked
            // for @Bork @Named("fred").  We "fell back" to
            // just @Bork.  Should we fall back to @Default, period?
            // Or @Any, and look for size-of-1?
        }
        final PersistenceUnitInfo returnValue = puInstance.get();
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    // Revisit: non-private for the moment only
    static PersistenceProvider getPersistenceProvider(final Instance<Object> instance,
                                                      final Set<Annotation> qualifiers,
                                                      final PersistenceUnitInfo persistenceUnitInfo)
        throws ReflectiveOperationException {
        final String cn = JpaExtension.class.getName();
        final String mn = "getPersistenceProvider";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {instance, qualifiers, persistenceUnitInfo});
        }
        Objects.requireNonNull(instance);
        Objects.requireNonNull(qualifiers);
        Objects.requireNonNull(persistenceUnitInfo);

        final PersistenceProvider returnValue;
        final String providerClassName = persistenceUnitInfo.getPersistenceProviderClassName();
        if (providerClassName == null) {
            returnValue = instance.select(PersistenceProvider.class,
                                          qualifiers.toArray(new Annotation[qualifiers.size()])).get();
        } else {
            returnValue =
                (PersistenceProvider) instance.select(Class.forName(providerClassName,
                                                                    true,
                                                                    Thread.currentThread().getContextClassLoader())).get();
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

}
