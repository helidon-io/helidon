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
import java.util.List;
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
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Vetoed;
import javax.enterprise.inject.literal.InjectLiteral;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.inject.spi.configurator.AnnotatedFieldConfigurator;
import javax.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import javax.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator;
import javax.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import javax.inject.Inject;
import javax.inject.Named;
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
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import io.helidon.integrations.cdi.jpa.PersistenceUnitInfoBean.DataSourceProvider;
import io.helidon.integrations.cdi.jpa.jaxb.Persistence;
import io.helidon.integrations.cdi.referencecountedcontext.ReferenceCounted;

import static javax.interceptor.Interceptor.Priority.LIBRARY_AFTER;
import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * A {@linkplain Extension portable extension} normally instantiated
 * by the Java {@linkplain java.util.ServiceLoader service provider
 * infrastructure} that integrates the provider-independent parts of
 * <a href="https://javaee.github.io/tutorial/partpersist.html#BNBPY"
 * target="_parent">JPA</a> into CDI.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>As with all CDI portable extensions, instances of this class are
 * <strong>not</strong> safe for concurrent use by multiple
 * threads.</p>
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
     * Indicates if JTA transactions can be supported.
     *
     * @see #divineTransactionSupport(ProcessAnnotatedType)
     */
    private boolean transactionsSupported;

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
     * <p>This field is {@linkplain Map#clear() cleared} at the
     * termination of the {@link
     * #afterBeanDiscovery(AfterBeanDiscovery, BeanManager)} container
     * lifecycle method.</p>
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
     *
     * <p>This field is {@linkplain Map#clear() cleared} at the
     * termination of the {@link
     * #afterBeanDiscovery(AfterBeanDiscovery, BeanManager)} container
     * lifecycle method.</p>
     */
    private final Map<String, Set<Class<?>>> unlistedManagedClassesByPersistenceUnitNames;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers annotating CDI
     * injection points related to JPA.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>This field is {@linkplain Collection#clear() cleared} at the
     * termination of the {@link
     * #afterBeanDiscovery(AfterBeanDiscovery, BeanManager)} container
     * lifecycle method.</p>
     */
    private final Set<Set<Annotation>> allQualifiers;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers for which
     * {@link CDITransactionScoped}-annotated {@link EntityManager}s
     * may be created.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>This field is {@linkplain Collection#clear() cleared} at the
     * termination of the {@link
     * #afterBeanDiscovery(AfterBeanDiscovery, BeanManager)} container
     * lifecycle method.</p>
     */
    private final Set<Set<Annotation>> cdiTransactionScopedEntityManagerQualifiers;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers for which
     * {@link NonTransactional}-annotated {@link EntityManager}s may
     * be created.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>This field is {@linkplain Collection#clear() cleared} at the
     * termination of the {@link
     * #afterBeanDiscovery(AfterBeanDiscovery, BeanManager)} container
     * lifecycle method.</p>
     */
    private final Set<Set<Annotation>> nonTransactionalEntityManagerQualifiers;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers for which
     * {@link ContainerManaged}-annotated {@link EntityManagerFactory}
     * instances may be created.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>This field is {@linkplain Collection#clear() cleared} at the
     * termination of the {@link
     * #afterBeanDiscovery(AfterBeanDiscovery, BeanManager)} container
     * lifecycle method.</p>
     */
    private final Set<Set<Annotation>> containerManagedEntityManagerFactoryQualifiers;

    /**
     * A feature flag set to {@code true} if a System property named
     * {@code jpaAnnotationRewritingEnabled} is {@link
     * Boolean#getBoolean(String) set to the <code>String</code> value
     * of <code>true</code>}, and indicates that {@link
     * PersistenceContext}-annotated JPA injection points should be
     * {@linkplain #rewriteJpaAnnotations(ProcessAnnotatedType)
     * "rewritten" to be CDI-compliant injection points}.
     *
     * <p>If the value of this field is {@code false}, then large
     * portions of this class will lie dormant.</p>
     *
     * @see #rewriteJpaAnnotations(ProcessAnnotatedType)
     */
    private final boolean jpaAnnotationRewritingEnabled;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link JpaExtension}.
     *
     * <p>Normally {@link JpaExtension} classes are created
     * automatically and only as needed by the CDI container.  End
     * users should have no need to create instances of this
     * class.</p>
     *
     * @see Extension
     */
    public JpaExtension() {
        super();
        this.jpaAnnotationRewritingEnabled = Boolean.getBoolean("jpaAnnotationRewritingEnabled");
        this.unlistedManagedClassesByPersistenceUnitNames = new HashMap<>();
        this.implicitPersistenceUnits = new HashMap<>();
        this.allQualifiers = new HashSet<>();
        this.cdiTransactionScopedEntityManagerQualifiers = new HashSet<>();
        this.containerManagedEntityManagerFactoryQualifiers = new HashSet<>();
        this.nonTransactionalEntityManagerQualifiers = new HashSet<>();
        // We start by presuming that JTA transactions can be
        // supported.  See the
        // #divineTransactionSupport(ProcessAnnotatedType) method
        // where this decision might be reversed.
        this.transactionsSupported = true;
    }


    /*
     * Instance methods.
     */


    /**
     * If {@code event} is non-{@code null}, then when this method is
     * invoked it irrevocably sets the {@link #transactionsSupported}
     * field to {@code false}.
     *
     * @param event a {@link ProcessAnnotatedType
     * ProcessAnnotatedType<}{@link NoTransactionSupport
     * NoTransactionSupport>} whose presence indicates that JTA
     * support is not available; will never be {@code null}
     */
    private void divineTransactionSupport(@Observes
                                          @Priority(LIBRARY_BEFORE)
                                          final ProcessAnnotatedType<NoTransactionSupport> event) {
        final String cn = JpaExtension.class.getName();
        final String mn = "divineTransactionSupport";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, event);
        }
        // If we receive an event of this type, then beans.xml
        // exclusions have fired such that it has been determined that
        // JTA is not loadable.  This of course means that JTA
        // transactions cannot be supported, and hence many (but not
        // all) features of JPA integration cannot be supported as
        // well.  See ../../../../../../resources/META-INF/beans.xml
        // and note the if-class-available and if-class-not-available
        // elements.
        this.transactionsSupported = false;
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Converts fields and setter methods annotated with {@link
     * PersistenceContext} to CDI injection points annotated with an
     * appropriate combination of {@link Inject}, {@link
     * ContainerManaged}, {@link Extended}, {@link
     * JPATransactionScoped}, {@link Synchronized} and/or {@link
     * Unsynchronized}.
     *
     * <p>This method does nothing if the {@link
     * #jpaAnnotationRewritingEnabled} field is {@code false}.</p>
     *
     * @param event the {@link ProcessAnnotatedType} container
     * lifecycle event being observed; must not be {@code null}
     */
    private <T> void rewriteJpaAnnotations(@Observes
                                           @WithAnnotations(PersistenceContext.class)
                                           final ProcessAnnotatedType<T> event) {
        final String cn = JpaExtension.class.getName();
        final String mn = "rewriteJpaAnnotations";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, event);
        }
        if (this.jpaAnnotationRewritingEnabled) {
            final AnnotatedTypeConfigurator<T> atc = event.configureAnnotatedType();
            atc.filterFields(JpaExtension::isEligiblePersistenceContextField)
                .forEach(JpaExtension::rewritePersistenceContextFieldAnnotations);
            atc.filterFields(JpaExtension::isEligiblePersistenceUnitField)
                .forEach(JpaExtension::rewritePersistenceUnitFieldAnnotations);
            atc.filterMethods(JpaExtension::isEligiblePersistenceContextSetterMethod)
                .forEach(JpaExtension::rewritePersistenceContextSetterMethodAnnotations);
            atc.filterMethods(JpaExtension::isEligiblePersistenceUnitSetterMethod)
                .forEach(JpaExtension::rewritePersistenceUnitSetterMethodAnnotations);
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

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
                                                // yes, @PersistenceContext, not @PersistenceUnit
                                                @WithAnnotations(PersistenceContext.class)
                                                final ProcessAnnotatedType<?> event,
                                                final BeanManager beanManager) {
        final String cn = JpaExtension.class.getName();
        final String mn = "gatherImplicitPersistenceUnits";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, beanManager});
        }
        if (event != null && beanManager != null) {
            final AnnotatedType<?> annotatedType = event.getAnnotatedType();
            if (annotatedType != null && !annotatedType.isAnnotationPresent(Vetoed.class)) {
                final Set<? extends PersistenceContext> persistenceContexts =
                    annotatedType.getAnnotations(PersistenceContext.class);
                if (persistenceContexts != null && !persistenceContexts.isEmpty()) {
                    for (final PersistenceContext persistenceContext : persistenceContexts) {
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
                                    final String persistencePropertyName = persistenceProperty.name();
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
                        final String name = persistenceContext.unitName(); // yes, unitName(), not name()
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
                        final String name = persistenceUnit.unitName(); // yes, unitName(), not name()
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
        if (managedClass != null && name != null && !name.isEmpty()) {
            Set<Class<?>> unlistedManagedClasses = this.unlistedManagedClassesByPersistenceUnitNames.get(name);
            if (unlistedManagedClasses == null) {
                unlistedManagedClasses = new HashSet<>();
                this.unlistedManagedClassesByPersistenceUnitNames.put(name, unlistedManagedClasses);
            }
            unlistedManagedClasses.add(managedClass);
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private <T extends EntityManager> void saveEntityManagerQualifiers(@Observes final ProcessInjectionPoint<?, T> event) {
        final String cn = JpaExtension.class.getName();
        final String mn = "saveEntityManagerQualifiers";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, event);
        }
        this.allQualifiers.add(event.getInjectionPoint().getQualifiers());
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Adds various beans that integrate JPA into CDI SE.
     *
     * <p>This method first converts {@code META-INF/persistence.xml}
     * resources into {@link PersistenceUnitInfo} objects and takes
     * into account any other {@link PersistenceUnitInfo} objects that
     * already exist and ensures that all of them are registered as
     * CDI beans.</p>
     *
     * <p>This allows other CDI-provider-specific mechanisms to use
     * these {@link PersistenceUnitInfo} beans as inputs for creating
     * {@link EntityManager} instances.</p>
     *
     * <p>Next, this method adds beans to produce {@link
     * EntityManager}s and {@link EntityManagerFactory} instances in
     * accordance with the JPA specification.</p>
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
    private void afterBeanDiscovery(@Observes
                                    @Priority(LIBRARY_AFTER)
                                    final AfterBeanDiscovery event,
                                    final BeanManager beanManager)
        throws IOException, JAXBException, ReflectiveOperationException, XMLStreamException {
        final String cn = JpaExtension.class.getName();
        final String mn = "afterBeanDiscovery";
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

        // Add synthetic beans to support JPA.  In some cases, JTA
        // must be present (see JPA section 7.5, for example: "A
        // container-managed entity manager must be a JTA entity
        // manager.").
        this.addContainerManagedJpaBeans(event, beanManager);

        // Clear out no-longer-needed-or-used collections to save
        // memory.
        this.allQualifiers.clear();
        this.cdiTransactionScopedEntityManagerQualifiers.clear();
        this.containerManagedEntityManagerFactoryQualifiers.clear();
        this.implicitPersistenceUnits.clear();
        this.nonTransactionalEntityManagerQualifiers.clear();
        this.unlistedManagedClassesByPersistenceUnitNames.clear();

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private void addContainerManagedJpaBeans(final AfterBeanDiscovery event, final BeanManager beanManager) {
        final String cn = JpaExtension.class.getName();
        final String mn = "addContainerManagedJpaBeans";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, beanManager});
        }
        if (this.transactionsSupported) {
            for (final Set<Annotation> qualifiers : this.allQualifiers) {
                // Note that each add* method invoked below is
                // responsible for ensuring that it adds beans only
                // once if at all, i.e. for validating the qualifiers
                // that it is supplied with.
                addContainerManagedEntityManagerFactory(event, qualifiers);
                addCDITransactionScopedEntityManager(event, qualifiers);
                if (qualifiers.contains(Extended.Literal.INSTANCE)) {
                    addExtendedEntityManager(event, qualifiers, beanManager);
                } else {
                    assert qualifiers.contains(JPATransactionScoped.Literal.INSTANCE);
                    addNonTransactionalEntityManager(event, qualifiers, beanManager);
                    addJPATransactionScopedEntityManager(event, qualifiers);
                }
            }
        } else {
            for (final Set<Annotation> qualifiers : this.allQualifiers) {
                // Note that each add* method invoked below is
                // responsible for ensuring that it adds beans only
                // once if at all, i.e. for validating the qualifiers
                // that it is supplied with.
                addContainerManagedEntityManagerFactory(event, qualifiers);
            }
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private void addContainerManagedEntityManagerFactory(final AfterBeanDiscovery event,
                                                         final Set<Annotation> suppliedQualifiers) {
        final String cn = JpaExtension.class.getName();
        final String mn = "addContainerManagedEntityManagerFactory";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, suppliedQualifiers});
        }
        Objects.requireNonNull(event);
        Objects.requireNonNull(suppliedQualifiers);
        // Provide support for, e.g.:
        //   @Inject
        //   @ContainerManaged
        //   @Named("test")
        //   private final EntityManagerFactory emf;
        final Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.removeAll(JpaCdiQualifiers.JPA_CDI_QUALIFIERS);
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        if (!this.containerManagedEntityManagerFactoryQualifiers.contains(qualifiers)) {
            this.containerManagedEntityManagerFactoryQualifiers.add(qualifiers);
            event.addBean()
                .addType(EntityManagerFactory.class)
                .scope(ApplicationScoped.class)
                .addQualifiers(qualifiers)
                .produceWith(instance -> produceContainerManagedEntityManagerFactory(instance, suppliedQualifiers))
                .disposeWith((emf, instance) -> emf.close());
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private void addCDITransactionScopedEntityManager(final AfterBeanDiscovery event,
                                                      final Set<Annotation> suppliedQualifiers) {
        final String cn = JpaExtension.class.getName();
        final String mn = "addCDITransactionScopedEntityManager";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, suppliedQualifiers});
        }
        Objects.requireNonNull(event);
        Objects.requireNonNull(suppliedQualifiers);
        if (!this.transactionsSupported) {
            throw new IllegalStateException();
        }
        // Provide support for, e.g.:
        //   @Inject
        //   @ContainerManaged
        //   @CDITransactionScoped
        //   @Synchronized // or @Unsynchronized, or none
        //   @Named("test")
        //   private final EntityManager cdiTransactionScopedEm;
        final Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        qualifiers.add(CDITransactionScoped.Literal.INSTANCE);
        qualifiers.remove(Extended.Literal.INSTANCE);
        qualifiers.remove(JPATransactionScoped.Literal.INSTANCE);
        if (this.cdiTransactionScopedEntityManagerQualifiers.add(qualifiers)) {
            final Class<? extends Annotation> scope;
            Class<? extends Annotation> temp = null;
            try {
                @SuppressWarnings("unchecked")
                    final Class<? extends Annotation> transactionScopedAnnotationClass =
                    (Class<? extends Annotation>) Class.forName("javax.transaction.TransactionScoped",
                                                                true,
                                                                Thread.currentThread().getContextClassLoader());
                temp = transactionScopedAnnotationClass;
            } catch (final ClassNotFoundException classNotFoundException) {
                // This will not happen if this.transactionsSupported
                // is true, or else CDI's exclusion mechanisms are
                // broken.  If somehow it does we throw a severe
                // error.
                throw new InternalError(classNotFoundException.getMessage(),
                                        classNotFoundException);
            } finally {
                scope = temp;
            }
            assert scope != null;
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.logp(Level.FINE, cn, mn, "Adding a CDI-TransactionScoped EntityManager with the following qualifiers: "
                            + qualifiers);
            }
            event.addBean()
                .addType(EntityManager.class)
                .addType(CDITransactionScopedEntityManager.class)
                .scope(scope)
                .addQualifiers(qualifiers)
                .produceWith(instance -> new CDITransactionScopedEntityManager(instance, suppliedQualifiers))
                .disposeWith((em, instance) -> em.close());
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private void addJPATransactionScopedEntityManager(final AfterBeanDiscovery event,
                                                      final Set<Annotation> suppliedQualifiers) {
        final String cn = JpaExtension.class.getName();
        final String mn = "addJPATransactionScopedEntityManager";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, suppliedQualifiers});
        }
        Objects.requireNonNull(event);
        Objects.requireNonNull(suppliedQualifiers);
        if (!this.transactionsSupported) {
            throw new IllegalStateException();
        }
        // Provide support for, e.g.:
        //   @Inject
        //   @ContainerManaged
        //   @JPATransactionScoped
        //   @Synchronized // or @Unsynchronized, or none
        //   @Named("test")
        //   private final EntityManager cdiTransactionScopedEm;
        final Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        qualifiers.add(JPATransactionScoped.Literal.INSTANCE);
        qualifiers.remove(CDITransactionScoped.Literal.INSTANCE);
        qualifiers.remove(Extended.Literal.INSTANCE);
        qualifiers.remove(NonTransactional.Literal.INSTANCE);
        event.addBean()
            .addType(EntityManager.class)
            .addType(JPATransactionScopedEntityManager.class)
            .scope(Dependent.class)
            .addQualifiers(qualifiers)
            .beanClass(JPATransactionScopedEntityManager.class)
            .produceWith(instance -> new JPATransactionScopedEntityManager(instance, suppliedQualifiers));
            // (deliberately no disposeWith())
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private void addNonTransactionalEntityManager(final AfterBeanDiscovery event,
                                                  final Set<Annotation> suppliedQualifiers,
                                                  final BeanManager beanManager) {
        final String cn = JpaExtension.class.getName();
        final String mn = "addNonTransactionalEntityManager";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, suppliedQualifiers});
        }
        Objects.requireNonNull(event);
        Objects.requireNonNull(suppliedQualifiers);
        Objects.requireNonNull(beanManager);
        // Provide support for, e.g.:
        //   @Inject
        //   @NonTransactional
        //   @Named("test")
        //   private final EntityManager nonTransactionalEm;
        final Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.removeAll(JpaCdiQualifiers.JPA_CDI_QUALIFIERS);
        qualifiers.add(NonTransactional.Literal.INSTANCE);
        if (!this.nonTransactionalEntityManagerQualifiers.contains(qualifiers)) {
            this.nonTransactionalEntityManagerQualifiers.add(qualifiers);
            event.addBean()
                .addType(EntityManager.class)
                .addType(NonTransactionalEntityManager.class)
                .scope(ReferenceCounted.class)
                .addQualifiers(qualifiers)
                .beanClass(NonTransactionalEntityManager.class)
                .produceWith(instance -> new NonTransactionalEntityManager(instance, suppliedQualifiers))
                .disposeWith((em, instance) -> em.close());
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private void addExtendedEntityManager(final AfterBeanDiscovery event,
                                          final Set<Annotation> suppliedQualifiers,
                                          final BeanManager beanManager) {
        final String cn = JpaExtension.class.getName();
        final String mn = "addExtendedEntityManager";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, suppliedQualifiers, beanManager});
        }
        Objects.requireNonNull(event);
        Objects.requireNonNull(suppliedQualifiers);
        Objects.requireNonNull(beanManager);
        if (!this.transactionsSupported) {
            throw new IllegalStateException();
        }
        // Provide support for, e.g.:
        //   @Inject
        //   @ContainerManaged
        //   @Extended
        //   @Synchronized // or @Unsynchronized, or none
        //   @Named("test")
        //   private final EntityManager nonTransactionalEm;
        final Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        qualifiers.add(Extended.Literal.INSTANCE);
        qualifiers.remove(JPATransactionScoped.Literal.INSTANCE);
        qualifiers.remove(CDITransactionScoped.Literal.INSTANCE);
        qualifiers.remove(NonTransactional.Literal.INSTANCE);
        event.addBean()
            .addType(EntityManager.class)
            .addType(ExtendedEntityManager.class)
            .scope(ReferenceCounted.class)
            .qualifiers(qualifiers)
            .beanClass(ExtendedEntityManager.class)
            .produceWith(instance -> new ExtendedEntityManager(instance, suppliedQualifiers, beanManager));
            // deliberately no disposeWith()
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
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
            final String persistenceUnitName = persistenceUnitInfoBean.getPersistenceUnitName();
            if (!persistenceUnitInfoBean.excludeUnlistedClasses()) {
                final Collection<? extends Class<?>> unlistedManagedClasses =
                    this.unlistedManagedClassesByPersistenceUnitNames.get(persistenceUnitName);
                if (unlistedManagedClasses != null && !unlistedManagedClasses.isEmpty()) {
                    for (final Class<?> unlistedManagedClass : unlistedManagedClasses) {
                        persistenceUnitInfoBean.addManagedClassName(unlistedManagedClass.getName());
                    }
                }
            }
            event.addBean()
                .types(Collections.singleton(PersistenceUnitInfo.class))
                .scope(ApplicationScoped.class)
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
            // We use StAX for XML loading because it is the same XML
            // parsing strategy used by all known CDI implementations.
            // If the end user wants to customize the StAX
            // implementation then we want that customization to apply
            // here as well.
            //
            // Note that XMLInputFactory is NOT deprecated in JDK 8:
            //   https://docs.oracle.com/javase/8/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory--
            //   ...but IS deprecated in JDK 9:
            //   https://docs.oracle.com/javase/9/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory--
            //   ...with an incorrect claim that it was deprecated
            //   since JDK 1.7.  In JDK 7 it actually was *not*
            //   deprecated:
            //   https://docs.oracle.com/javase/7/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory()
            //   ...and now in JDK 10 it is NO LONGER deprecated:
            //   https://docs.oracle.com/javase/10/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory()
            //   ...nor in JDK 11:
            //   https://docs.oracle.com/en/java/javase/11/docs/api/java.xml/javax/xml/stream/XMLInputFactory.html#newFactory()
            //   ...nor in JDK 12:
            //   https://docs.oracle.com/en/java/javase/12/docs/api/java.xml/javax/xml/stream/XMLInputFactory.html#newFactory()
            //   So we suppress deprecation warnings since deprecation
            //   in JDK 9 appears to have been a mistake.
            @SuppressWarnings("deprecation")
            final XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();

            // See
            // https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.md#xmlinputfactory-a-stax-parser
            xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            xmlInputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);

            final Unmarshaller unmarshaller =
                JAXBContext.newInstance(Persistence.class.getPackage().getName()).createUnmarshaller();
            final DataSourceProvider dataSourceProvider = new BeanManagerBackedDataSourceProvider(beanManager);
            while (urls.hasMoreElements()) {
                final URL url = urls.nextElement();
                assert url != null;
                Collection<? extends PersistenceUnitInfo> persistenceUnitInfos = null;
                try (InputStream inputStream = new BufferedInputStream(url.openStream())) {
                    final XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(inputStream);
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
                            .scope(ApplicationScoped.class)
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

    /**
     * {@linkplain
     * PersistenceProvider#createContainerEntityManagerFactory(PersistenceUnitInfo,
     * Map) Creates and returns a new
     * <code>EntityManagerFactory</code>} when invoked, honoring
     * sections 3.6.2, 9.1, 9.5 and 9.5.1 of the JPA 2.2
     * specification.
     *
     * @param instance an {@link Instance} used to acquire contextual
     * references; must not be {@code null}
     *
     * @param suppliedQualifiers qualifiers that will qualify the
     * returned {@link EntityManagerFactory}; must not be {@code null}
     *
     * @see
     * PersistenceProvider#createContainerEntityManagerFactory(PersistenceUnitInfo,
     * Map)
     */
    private EntityManagerFactory produceContainerManagedEntityManagerFactory(final Instance<Object> instance,
                                                                             final Set<Annotation> suppliedQualifiers) {
        final String cn = JpaExtension.class.getName();
        final String mn = "produceContainerManagedEntityManagerFactory";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {instance, suppliedQualifiers});
        }
        Objects.requireNonNull(instance);
        Objects.requireNonNull(suppliedQualifiers);
        final PersistenceUnitInfo pu = getPersistenceUnitInfo(instance, suppliedQualifiers);
        if (PersistenceUnitTransactionType.RESOURCE_LOCAL.equals(pu.getTransactionType())) {
            throw new CreationException(); // Revisit: message
        }
        PersistenceProvider persistenceProvider = null;
        try {
            persistenceProvider = getPersistenceProvider(instance, suppliedQualifiers, pu);
        } catch (final ReflectiveOperationException reflectiveOperationException) {
            throw new CreationException(reflectiveOperationException.getMessage(),
                                        reflectiveOperationException);
        }
        // Revisit: there should be a way to acquire these, or maybe
        // not, given that we can source PersistenceUnitInfos as beans
        // anyway.
        final Map<String, Object> properties = new HashMap<>();
        final BeanManager beanManager = instance.select(BeanManager.class).get();
        properties.put("javax.persistence.bean.manager", beanManager);
        Class<?> validatorFactoryClass = null;
        try {
            validatorFactoryClass = Class.forName("javax.validation.ValidatorFactory");
        } catch (final ClassNotFoundException classNotFoundException) {
            // Revisit: log
        }
        if (validatorFactoryClass != null) {
            final Bean<?> vfb = getValidatorFactoryBean(beanManager, validatorFactoryClass);
            if (vfb != null) {
                final CreationalContext<?> cc = beanManager.createCreationalContext(vfb);
                properties.put("javax.persistence.validation.factory", beanManager.getReference(vfb, validatorFactoryClass, cc));
            }
        }
        final EntityManagerFactory returnValue = persistenceProvider.createContainerEntityManagerFactory(pu, properties);
        assert returnValue != null;
        assert returnValue.isOpen();
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }


    /*
     * Static methods.
     */


    /**
     * Returns {@code true} if the supplied {@link AnnotatedField} is
     * annotated with {@link PersistenceContext}, is not annotated
     * with {@link Inject} and has a type assignable to {@link
     * EntityManager}.
     *
     * @param f the {@link AnnotatedField} in question; may be {@code
     * null} in which case {@code false} will be returned
     *
     * @return {@code true} if the supplied {@link AnnotatedField} is
     * annotated with {@link PersistenceContext}, is not annotated
     * with {@link Inject} and has a type assignable to {@link
     * EntityManager}; {@code false} in all other cases
     */
    private static <T> boolean isEligiblePersistenceContextField(final AnnotatedField<T> f) {
        final String cn = JpaExtension.class.getName();
        final String mn = "isEligiblePersistenceContextField";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, f);
        }
        final boolean returnValue;
        if (f != null
            && f.isAnnotationPresent(PersistenceContext.class)
            && !f.isAnnotationPresent(Inject.class)) {
            final Type fieldType = f.getBaseType();
            returnValue = fieldType instanceof Class && EntityManager.class.isAssignableFrom((Class<?>) fieldType);
        } else {
            returnValue = false;
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, Boolean.valueOf(returnValue));
        }
        return returnValue;
    }

    /**
     * Returns {@code true} if the supplied {@link AnnotatedField} is
     * annotated with {@link PersistenceUnit}, is not annotated
     * with {@link Inject} and has a type assignable to {@link
     * EntityManagerFactory}.
     *
     * @param f the {@link AnnotatedField} in question; may be {@code
     * null} in which case {@code false} will be returned
     *
     * @return {@code true} if the supplied {@link AnnotatedField} is
     * annotated with {@link PersistenceUnit}, is not annotated with
     * {@link Inject} and has a type assignable to {@link
     * EntityManagerFactory}; {@code false} in all other cases
     */
    private static <T> boolean isEligiblePersistenceUnitField(final AnnotatedField<T> f) {
        final String cn = JpaExtension.class.getName();
        final String mn = "isEligiblePersistenceUnitField";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, f);
        }
        final boolean returnValue;
        if (f != null
            && f.isAnnotationPresent(PersistenceUnit.class)
            && !f.isAnnotationPresent(Inject.class)) {
            final Type fieldType = f.getBaseType();
            returnValue = fieldType instanceof Class && EntityManagerFactory.class.isAssignableFrom((Class<?>) fieldType);
        } else {
            returnValue = false;
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, Boolean.valueOf(returnValue));
        }
        return returnValue;
    }

    /**
     * Reconfigures annotations on an {@linkplain
     * #isEligiblePersistenceContextField(AnnotatedField) eligible
     * <code>PersistenceContext</code>-annotated
     * <code>AnnotatedField</code>} such that the resulting {@link
     * AnnotatedField} is a true CDI injection point representing all
     * the same information.
     *
     * <p>The original {@link PersistenceContext} annotation is
     * removed.</p>
     *
     * @param fc the {@link AnnotatedFieldConfigurator} that allows
     * the field to be re-annotated; must not be {@code null}
     *
     * @exception NullPointerException if {@code fc} is {@code null}
     */
    private static <T> void rewritePersistenceContextFieldAnnotations(final AnnotatedFieldConfigurator<T> fc) {
        final String cn = JpaExtension.class.getName();
        final String mn = "rewritePersistenceContextFieldAnnotations";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, fc);
        }
        Objects.requireNonNull(fc);
        final PersistenceContext pc = fc.getAnnotated().getAnnotation(PersistenceContext.class);
        if (pc != null) {
            fc.remove(a -> a == pc);
        }
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
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Reconfigures annotations on an {@linkplain
     * #isEligiblePersistenceUnitField(AnnotatedField) eligible
     * <code>PersistenceUnit</code>-annotated
     * <code>AnnotatedField</code>} such that the resulting {@link
     * AnnotatedField} is a true CDI injection point representing all
     * the same information.
     *
     * <p>The original {@link PersistenceUnit} annotation is
     * removed.</p>
     *
     * @param fc the {@link AnnotatedFieldConfigurator} that allows
     * the field to be re-annotated; must not be {@code null}
     *
     * @exception NullPointerException if {@code fc} is {@code null}
     */
    private static <T> void rewritePersistenceUnitFieldAnnotations(final AnnotatedFieldConfigurator<T> fc) {
        final String cn = JpaExtension.class.getName();
        final String mn = "rewritePersistenceUnitFieldAnnotations";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, fc);
        }
        Objects.requireNonNull(fc);
        final PersistenceUnit pu = fc.getAnnotated().getAnnotation(PersistenceUnit.class);
        if (pu != null) {
            fc.remove(a -> a == pu);
        }
        fc.add(InjectLiteral.INSTANCE);
        fc.add(ContainerManaged.Literal.INSTANCE);
        fc.add(NamedLiteral.of(pu.unitName().trim()));
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Returns {@code true} if the supplied {@link AnnotatedMethod} is
     * annotated with {@link PersistenceContext}, is not annotated
     * with {@link Inject} and has at least one parameter whose type
     * is assignable to {@link EntityManager}.
     *
     * @param m the {@link AnnotatedMethod} in question; may be {@code
     * null} in which case {@code false} will be returned
     *
     * @return {@code true} if the supplied {@link AnnotatedMethod} is
     * annotated with {@link PersistenceContext}, is not annotated
     * with {@link Inject} and has at least one parameter whose type
     * is assignable to {@link EntityManager}
     */
    private static <T> boolean isEligiblePersistenceContextSetterMethod(final AnnotatedMethod<T> m) {
        final String cn = JpaExtension.class.getName();
        final String mn = "isEligiblePersistenceContextSetterMethod";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, m);
        }
        final boolean returnValue;
        if (m != null
            && m.isAnnotationPresent(PersistenceContext.class)
            && !m.isAnnotationPresent(Inject.class)) {
            final List<AnnotatedParameter<T>> parameters = m.getParameters();
            if (parameters != null && !parameters.isEmpty()) {
                boolean temp = false;
                for (final Annotated parameter : parameters) {
                    final Type type = parameter.getBaseType();
                    if (type instanceof Class && EntityManager.class.isAssignableFrom((Class<?>) type)) {
                        if (temp) {
                            temp = false;
                            break;
                        } else {
                            temp = true;
                        }
                    }
                }
                returnValue = temp;
            } else {
                returnValue = false;
            }
        } else {
            returnValue = false;
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, Boolean.valueOf(returnValue));
        }
        return returnValue;
    }

    /**
     * Returns {@code true} if the supplied {@link AnnotatedMethod} is
     * annotated with {@link PersistenceUnit}, is not annotated with
     * {@link Inject} and has at least one parameter whose type is
     * assignable to {@link EntityManagerFactory}.
     *
     * @param m the {@link AnnotatedMethod} in question; may be {@code
     * null} in which case {@code false} will be returned
     *
     * @return {@code true} if the supplied {@link AnnotatedMethod} is
     * annotated with {@link PersistenceUnit}, is not annotated with
     * {@link Inject} and has at least one parameter whose type is
     * assignable to {@link EntityManagerFactory}
     */
    private static <T> boolean isEligiblePersistenceUnitSetterMethod(final AnnotatedMethod<T> m) {
        final String cn = JpaExtension.class.getName();
        final String mn = "isEligiblePersistenceUnitSetterMethod";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, m);
        }
        final boolean returnValue;
        if (m != null
            && m.isAnnotationPresent(PersistenceUnit.class)
            && !m.isAnnotationPresent(Inject.class)) {
            final List<AnnotatedParameter<T>> parameters = m.getParameters();
            if (parameters != null && !parameters.isEmpty()) {
                boolean temp = false;
                for (final Annotated parameter : parameters) {
                    final Type type = parameter.getBaseType();
                    if (type instanceof Class && EntityManagerFactory.class.isAssignableFrom((Class<?>) type)) {
                        if (temp) {
                            temp = false;
                            break;
                        } else {
                            temp = true;
                        }
                    }
                }
                returnValue = temp;
            } else {
                returnValue = false;
            }
        } else {
            returnValue = false;
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, Boolean.valueOf(returnValue));
        }
        return returnValue;
    }

    private static <T> void rewritePersistenceContextSetterMethodAnnotations(final AnnotatedMethodConfigurator<T> mc) {
        final String cn = JpaExtension.class.getName();
        final String mn = "rewritePersistenceContextSetterMethodAnnotations";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, mc);
        }
        Objects.requireNonNull(mc);
        final Annotated annotated = mc.getAnnotated();
        if (!annotated.isAnnotationPresent(Inject.class)) {
            final PersistenceContext pc = annotated.getAnnotation(PersistenceContext.class);
            if (pc != null) {
                boolean observerMethod = false;
                final List<AnnotatedParameterConfigurator<T>> parameters = mc.params();
                if (parameters != null && !parameters.isEmpty()) {
                    for (final AnnotatedParameterConfigurator<T> apc : parameters) {
                        if (apc != null) {
                            final Annotated parameter = apc.getAnnotated();
                            if (!observerMethod) {
                                observerMethod = parameter.isAnnotationPresent(Observes.class);
                            }
                            final Type parameterType = parameter.getBaseType();
                            if (parameterType instanceof Class
                                && EntityManager.class.isAssignableFrom((Class<?>) parameterType)) {
                                apc.add(ContainerManaged.Literal.INSTANCE);
                                if (PersistenceContextType.EXTENDED.equals(pc.type())) {
                                    apc.add(Extended.Literal.INSTANCE);
                                } else {
                                    apc.add(JPATransactionScoped.Literal.INSTANCE);
                                }
                                if (SynchronizationType.UNSYNCHRONIZED.equals(pc.synchronization())) {
                                    apc.add(Unsynchronized.Literal.INSTANCE);
                                } else {
                                    apc.add(Synchronized.Literal.INSTANCE);
                                }
                                apc.add(NamedLiteral.of(pc.unitName().trim()));
                            }
                        }
                    }
                }
                mc.remove(a -> a == pc);
                if (!observerMethod) {
                    mc.add(InjectLiteral.INSTANCE);
                }
            }
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private static <T> void rewritePersistenceUnitSetterMethodAnnotations(final AnnotatedMethodConfigurator<T> mc) {
        final String cn = JpaExtension.class.getName();
        final String mn = "rewritePersistenceUnitSetterMethodAnnotations";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, mc);
        }
        Objects.requireNonNull(mc);
        final Annotated annotated = mc.getAnnotated();
        if (!annotated.isAnnotationPresent(Inject.class)) {
            final PersistenceUnit pu = annotated.getAnnotation(PersistenceUnit.class);
            if (pu != null) {
                boolean observerMethod = false;
                final List<AnnotatedParameterConfigurator<T>> parameters = mc.params();
                if (parameters != null && !parameters.isEmpty()) {
                    for (final AnnotatedParameterConfigurator<T> apc : parameters) {
                        if (apc != null) {
                            final Annotated parameter = apc.getAnnotated();
                            if (!observerMethod) {
                                observerMethod = parameter.isAnnotationPresent(Observes.class);
                            }
                            final Type parameterType = parameter.getBaseType();
                            if (parameterType instanceof Class
                                && EntityManagerFactory.class.isAssignableFrom((Class<?>) parameterType)) {
                                apc.add(ContainerManaged.Literal.INSTANCE);
                                apc.add(NamedLiteral.of(pu.unitName().trim()));
                            }
                        }
                    }
                }
                mc.remove(a -> a == pu);
                if (!observerMethod) {
                    mc.add(InjectLiteral.INSTANCE);
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
                try {
                    maybeAddPersistenceProviderBean(event, pui, providers);
                } finally {
                    preexistingPersistenceUnitInfoBean.destroy(pui, cc);
                    cc.release();
                }
            }
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
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
            .scope(ApplicationScoped.class)
            .createWith(cc -> resolver);
        final Collection<? extends PersistenceProvider> providers = resolver.getPersistenceProviders();
        for (final PersistenceProvider provider : providers) {
            event.addBean()
                .addTransitiveTypeClosure(provider.getClass())
                .scope(ApplicationScoped.class)
                .createWith(cc -> provider);
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, providers);
        }
        return providers;
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
                    .scope(ApplicationScoped.class)
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

    private static PersistenceUnitInfo getPersistenceUnitInfo(final Instance<Object> instance,
                                                              final Set<Annotation> suppliedQualifiers) {
        final String cn = JpaExtension.class.getName();
        final String mn = "getPersistenceUnitInfo";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {instance, suppliedQualifiers});
        }
        Objects.requireNonNull(instance);
        Objects.requireNonNull(suppliedQualifiers);
        final Set<Annotation> selectionQualifiers = new HashSet<>(suppliedQualifiers);
        selectionQualifiers.remove(Any.Literal.INSTANCE);
        selectionQualifiers.removeAll(JpaCdiQualifiers.JPA_CDI_QUALIFIERS);
        Instance<PersistenceUnitInfo> puInstance;
        if (selectionQualifiers.isEmpty()) {
            puInstance = instance.select(PersistenceUnitInfo.class);
        } else {
            puInstance = instance.select(PersistenceUnitInfo.class,
                                         selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]));
            if (puInstance.isUnsatisfied()) {
                // We looked for @Qualifier @Named("x"); now look for
                // just @Qualifier...
                selectionQualifiers.removeIf(q -> q instanceof Named);
                puInstance = instance.select(PersistenceUnitInfo.class,
                                             selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]));
                if (puInstance.isUnsatisfied() && !selectionQualifiers.equals(Collections.singleton(Default.Literal.INSTANCE))) {
                    // ...now just @Default...
                    puInstance = instance.select(PersistenceUnitInfo.class);
                    if (puInstance.isUnsatisfied()) {
                        // ...now any at all.  Obviously this case
                        // will resolve only if there is exactly one
                        // PersistenceUnitInfo bean, and somewhat
                        // bizarrely it won't have the @Default
                        // qualifier.
                        puInstance = instance.select(PersistenceUnitInfo.class, Any.Literal.INSTANCE);
                    }
                }
            }
        }
        // This may very well throw a resolution exception; that is
        // anticipated.
        final PersistenceUnitInfo returnValue = puInstance.get();
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn, returnValue);
        }
        return returnValue;
    }

    private static PersistenceProvider getPersistenceProvider(final Instance<Object> instance,
                                                              final Set<Annotation> suppliedQualifiers,
                                                              final PersistenceUnitInfo persistenceUnitInfo)
        throws ReflectiveOperationException {
        final String cn = JpaExtension.class.getName();
        final String mn = "getPersistenceProvider";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {instance, suppliedQualifiers, persistenceUnitInfo});
        }
        Objects.requireNonNull(instance);
        Objects.requireNonNull(suppliedQualifiers);
        Objects.requireNonNull(persistenceUnitInfo);
        final Set<Annotation> selectionQualifiers = new HashSet<>(suppliedQualifiers);
        selectionQualifiers.remove(Any.Literal.INSTANCE);
        selectionQualifiers.removeAll(JpaCdiQualifiers.JPA_CDI_QUALIFIERS);
        selectionQualifiers.removeIf(q -> q instanceof Named);
        final Annotation[] selectionQualifiersArray =
            selectionQualifiers.toArray(new Annotation[selectionQualifiers.size()]);
        final PersistenceProvider returnValue;
        final String providerClassName = persistenceUnitInfo.getPersistenceProviderClassName();
        if (providerClassName == null) {
            returnValue = instance.select(PersistenceProvider.class, selectionQualifiersArray).get();
        } else {
            returnValue =
                (PersistenceProvider) instance.select(Class.forName(providerClassName,
                                                                    true,
                                                                    Thread.currentThread().getContextClassLoader()),
                                                      selectionQualifiersArray).get();
        }
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

}
