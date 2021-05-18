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
import java.util.ArrayList;
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
import javax.enterprise.context.Initialized;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.CreationException;
import javax.enterprise.inject.InjectionException;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Vetoed;
import javax.enterprise.inject.literal.InjectLiteral;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
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
    private static final Logger LOGGER = Logger.getLogger(JpaExtension.class.getName(),
                                                          JpaExtension.class.getPackage().getName() + ".Messages");

    /**
     * The name used to designate the only persistence unit in the
     * environment, when there is exactly one persistence unit in the
     * environment, and there is at least one {@link
     * PersistenceContext @PersistenceContext}-annotated injection
     * point that does not specify a value for the {@link
     * PersistenceContext#unitName() unitName} element.
     *
     * <p>In such a case, the injection point will be effectively
     * rewritten such that it will appear to the CDI container as
     * though there <em>were</em> a value specified for the {@link
     * PersistenceContext#unitName() unitName} element&mdash;namely
     * this field's value.  Additionally, a bean identical to the
     * existing solitary {@link PersistenceUnitInfo}-typed bean will
     * be added with this field's value as the {@linkplain
     * Named#value() value of its <code>Named</code> qualifier}, thus
     * serving as a kind of alias for the "real" bean.</p>
     *
     * <p>This is necessary because the empty string ({@code ""}) as
     * the value of the {@link Named#value()} element has special
     * semantics, so cannot be used to designate an unnamed
     * persistence unit.</p>
     *
     * <p>The value of this field is subject to change without prior
     * notice at any point.  In general the mechanics around injection
     * point rewriting are also subject to change without prior notice
     * at any point.</p>
     */
    static final String DEFAULT_PERSISTENCE_UNIT_NAME = "__DEFAULT__";


    /*
     * Instance fields.
     */


    /**
     * Indicates if JTA transactions can be supported.
     *
     * @see #disableTransactionSupport(ProcessAnnotatedType)
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
     * <p>These qualifiers are built up as this portable extension
     * {@linkplain ProcessInjectionPoint discovers {@link
     * EntityManager}-typed <code>InjectionPoint</code>s}.</p>
     *
     * <p>This field is {@linkplain Collection#clear() cleared} at the
     * termination of the {@link
     * #afterBeanDiscovery(AfterBeanDiscovery, BeanManager)} container
     * lifecycle method.</p>
     *
     * @see #saveEntityManagerQualifiers(ProcessInjectionPoint)
     */
    private final Set<Set<Annotation>> persistenceContextQualifiers;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers for which
     * {@link EntityManagerFactory} beans may be created.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>These qualifiers are built up as this portable extension
     * {@linkplain ProcessInjectionPoint discovers {@link
     * EntityManagerFactory}-typed <code>InjectionPoint</code>s}.</p>
     *
     * <p>This field is {@linkplain Collection#clear() cleared} at the
     * termination of the {@link
     * #afterBeanDiscovery(AfterBeanDiscovery, BeanManager)} container
     * lifecycle method.</p>
     *
     * @see #saveEntityManagerFactoryQualifiers(ProcessInjectionPoint)
     */
    private final Set<Set<Annotation>> persistenceUnitQualifiers;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers that serves as
     * a kind of cache, preventing more than one {@link
     * CdiTransactionScoped}-qualified {@link EntityManager}-typed
     * bean from being added for the same set of qualifiers.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>This field is {@linkplain Collection#clear() cleared} at the
     * termination of the {@link
     * #afterBeanDiscovery(AfterBeanDiscovery, BeanManager)} container
     * lifecycle method.</p>
     *
     * @see
     * #addCdiTransactionScopedEntityManagerBeans(AfterBeanDiscovery,
     * Set)
     */
    private final Set<Set<Annotation>> cdiTransactionScopedEntityManagerQualifiers;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers that serves as
     * a kind of cache, preventing more than one {@link
     * NonTransactional}-qualified {@link EntityManager}-typed bean
     * from being added for the same set of qualifiers.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>This field is {@linkplain Collection#clear() cleared} at the
     * termination of the {@link
     * #afterBeanDiscovery(AfterBeanDiscovery, BeanManager)} container
     * lifecycle method.</p>
     *
     * @see #addNonTransactionalEntityManagerBeans(AfterBeanDiscovery,
     * Set, BeanManager)
     */
    private final Set<Set<Annotation>> nonTransactionalEntityManagerQualifiers;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers that serves as
     * a kind of cache, preventing more than one {@link
     * ContainerManaged}-qualified {@link EntityManagerFactory}-typed
     * bean from being added for the same set of qualifiers.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>This field is {@linkplain Collection#clear() cleared} at the
     * termination of the {@link
     * #afterBeanDiscovery(AfterBeanDiscovery, BeanManager)} container
     * lifecycle method.</p>
     *
     * @see
     * #addContainerManagedEntityManagerFactoryBeans(AfterBeanDiscovery,
     * Set, BeanManager)
     */
    private final Set<Set<Annotation>> containerManagedEntityManagerFactoryQualifiers;

    /**
     * Indicates whether an injection point has called for the default
     * persistence unit.  This has implications on how beans are
     * installed.
     *
     * @see #validate(AfterDeploymentValidation)
     */
    private boolean defaultPersistenceUnitInEffect;

    /**
     * Indicates whether a bean for the default persistence unit
     * has been added.
     *
     * @see #validate(AfterDeploymentValidation)
     */
    private boolean addedDefaultPersistenceUnit;


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
        final String cn = JpaExtension.class.getName();
        final String mn = "<init>";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn);
        }
        this.unlistedManagedClassesByPersistenceUnitNames = new HashMap<>();
        this.implicitPersistenceUnits = new HashMap<>();
        this.persistenceContextQualifiers = new HashSet<>();
        this.cdiTransactionScopedEntityManagerQualifiers = new HashSet<>();
        this.containerManagedEntityManagerFactoryQualifiers = new HashSet<>();
        this.nonTransactionalEntityManagerQualifiers = new HashSet<>();
        this.persistenceUnitQualifiers = new HashSet<>();
        // We start by presuming that JTA transactions can be
        // supported.  See the
        // #disableTransactionSupport(ProcessAnnotatedType) method
        // where this decision might be reversed.
        this.transactionsSupported = true;
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
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
     * support is not available; must not be {@code null}
     *
     * @exception NullPointerException if {@code event} is {@code
     * null}
     *
     * @see #transactionsSupported
     *
     * @see NoTransactionSupport
     */
    private void disableTransactionSupport(@Observes
                                           @Priority(LIBRARY_BEFORE)
                                           final ProcessAnnotatedType<NoTransactionSupport> event) {
        final String cn = JpaExtension.class.getName();
        final String mn = "disableTransactionSupport";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, event);
        }

        Objects.requireNonNull(event);

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
     * JpaTransactionScoped}, {@link Synchronized} and/or {@link
     * Unsynchronized}.
     *
     * @param event the {@link ProcessAnnotatedType} container
     * lifecycle event being observed; must not be {@code null}
     *
     * @exception NullPointerException if {@code event} is {@code null}
     */
    private <T> void rewriteJpaAnnotations(@Observes
                                           @WithAnnotations({
                                               PersistenceContext.class,
                                               PersistenceUnit.class
                                           })
                                           final ProcessAnnotatedType<T> event) {
        final String cn = JpaExtension.class.getName();
        final String mn = "rewriteJpaAnnotations";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, event);
        }

        Objects.requireNonNull(event);

        final AnnotatedTypeConfigurator<T> atc = event.configureAnnotatedType();
        atc.filterFields(JpaExtension::isEligiblePersistenceContextField)
            .forEach(this::rewritePersistenceContextFieldAnnotations);
        atc.filterFields(JpaExtension::isEligiblePersistenceUnitField)
            .forEach(this::rewritePersistenceUnitFieldAnnotations);
        atc.filterMethods(JpaExtension::isEligiblePersistenceContextSetterMethod)
            .forEach(this::rewritePersistenceContextSetterMethodAnnotations);
        atc.filterMethods(JpaExtension::isEligiblePersistenceUnitSetterMethod)
            .forEach(this::rewritePersistenceUnitSetterMethodAnnotations);

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
     * must not be {@code null}
     *
     * @param beanManager the {@link BeanManager} in effect; must not
     * be {@code null}
     *
     * @exception NullPointerException if either {@code event} or
     * {@code beanManager} is {@code null}
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

        Objects.requireNonNull(event);
        Objects.requireNonNull(beanManager);

        final AnnotatedType<?> annotatedType = event.getAnnotatedType();
        if (annotatedType != null && !annotatedType.isAnnotationPresent(Vetoed.class)) {
            final Set<? extends PersistenceContext> persistenceContexts =
                annotatedType.getAnnotations(PersistenceContext.class);
            if (persistenceContexts != null && !persistenceContexts.isEmpty()) {
                for (final PersistenceContext persistenceContext : persistenceContexts) {
                    if (LOGGER.isLoggable(Level.INFO)) {
                        final String name = persistenceContext.name().trim();
                        if (!name.isEmpty()) {
                            LOGGER.logp(Level.INFO, cn, mn,
                                        "persistenceContextNameIgnored", new Object[] {annotatedType, name});
                        }
                    }
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
                                                            () -> beanManager
                                                                .createInstance()
                                                                .select(DataSourceProvider.class)
                                                                .get(),
                                                            properties);
                            this.implicitPersistenceUnits.put(persistenceUnitName, persistenceUnit);
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
     * being processed; must not be {@code null}
     *
     * @exception NullPointerException if {@code event} is {@code
     * null}
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

        Objects.requireNonNull(event);

        final AnnotatedType<?> annotatedType = event.getAnnotatedType();
        if (annotatedType != null && !annotatedType.isAnnotationPresent(Vetoed.class)) {
            this.assignManagedClassToPersistenceUnit(annotatedType.getAnnotations(PersistenceContext.class),
                                                     annotatedType.getAnnotations(PersistenceUnit.class),
                                                     annotatedType.getJavaClass());
            event.veto(); // managed classes can't be beans
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
                        String unitName = persistenceContext.unitName();
                        if (unitName == null || unitName.isEmpty()) {
                            unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                            this.defaultPersistenceUnitInEffect = true;
                        }
                        processed = true;
                        addUnlistedManagedClass(unitName, c);
                    }
                }
            }
            if (persistenceUnits != null && !persistenceUnits.isEmpty()) {
                for (final PersistenceUnit persistenceUnit : persistenceUnits) {
                    if (persistenceUnit != null) {
                        String unitName = persistenceUnit.unitName();
                        if (unitName == null || unitName.isEmpty()) {
                            unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                            this.defaultPersistenceUnitInEffect = true;
                        }
                        processed = true;
                        addUnlistedManagedClass(unitName, c);
                    }
                }
            }
            if (!processed) {
                addUnlistedManagedClass(DEFAULT_PERSISTENCE_UNIT_NAME, c);
                this.defaultPersistenceUnitInEffect = true;
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
     * be {@code null}
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
            if (name == null || name.isEmpty()) {
                name = DEFAULT_PERSISTENCE_UNIT_NAME;
                this.defaultPersistenceUnitInEffect = true;
            }
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

    /**
     * Stores {@link Set}s of qualifiers that annotate {@link
     * EntityManagerFactory}-typed injection points.
     *
     * <p>{@link EntityManagerFactory}-typed beans will be added for
     * each such {@link Set}.</p>
     *
     * @param event a {@link ProcessInjectionPoint} container
     * lifecycle event; must not be {@code null}
     *
     * @exception NullPointerException if {@code event} is {@code
     * null}
     */
    private <T extends EntityManagerFactory> void saveEntityManagerFactoryQualifiers(@Observes
                                                                                     final ProcessInjectionPoint<?, T> event) {
        final String cn = JpaExtension.class.getName();
        final String mn = "saveEntityManagerFactoryQualifiers";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, event);
        }

        Objects.requireNonNull(event);

        final InjectionPoint injectionPoint = event.getInjectionPoint();
        assert injectionPoint != null;

        this.persistenceUnitQualifiers.add(injectionPoint.getQualifiers());

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Stores {@link Set}s of qualifiers that annotate {@link
     * EntityManager}-typed injection points.
     *
     * <p>{@link EntityManager}-typed beans will be added for each
     * such {@link Set}.</p>
     *
     * @param event a {@link ProcessInjectionPoint} container
     * lifecycle event; must not be {@code null}
     *
     * @exception NullPointerException if {@code event} is {@code
     * null}
     */
    private <T extends EntityManager> void saveEntityManagerQualifiers(@Observes
                                                                       final ProcessInjectionPoint<?, T> event) {
        final String cn = JpaExtension.class.getName();
        final String mn = "saveEntityManagerQualifiers";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, event);
        }

        Objects.requireNonNull(event);

        final InjectionPoint injectionPoint = event.getInjectionPoint();
        assert injectionPoint != null;

        final Set<Annotation> qualifiers = injectionPoint.getQualifiers();
        assert qualifiers != null;
        boolean error = false;
        if (qualifiers.contains(JpaTransactionScoped.Literal.INSTANCE)) {
            if (qualifiers.contains(CdiTransactionScoped.Literal.INSTANCE)
                || qualifiers.contains(Extended.Literal.INSTANCE)
                || qualifiers.contains(NonTransactional.Literal.INSTANCE)) {
                error = true;
            }
        } else if (qualifiers.contains(Extended.Literal.INSTANCE)) {
            if (qualifiers.contains(CdiTransactionScoped.Literal.INSTANCE)
                || qualifiers.contains(NonTransactional.Literal.INSTANCE)) {
                error = true;
            }
        } else if (qualifiers.contains(NonTransactional.Literal.INSTANCE)) {
            if (qualifiers.contains(CdiTransactionScoped.Literal.INSTANCE)) {
                error = true;
            }
        } else if (qualifiers.contains(Synchronized.Literal.INSTANCE)) {
            if (qualifiers.contains(Unsynchronized.Literal.INSTANCE)) {
                error = true;
            }
        }
        if (error) {
            event.addDefinitionError(new InjectionException("Invalid injection point; some qualifiers are mutually exclusive: "
                                                            + qualifiers));
        } else {
            this.persistenceContextQualifiers.add(qualifiers);
        }

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
            this.maybeAddPersistenceProviderBeans(event, beanManager, preexistingPersistenceUnitInfoBeans, providers);
        }

        // Next, and most commonly, load all META-INF/persistence.xml
        // resources with JAXB, and turn them into PersistenceUnitInfo
        // instances, and add beans for all of them as well as their
        // associated PersistenceProviders (if applicable).
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final Enumeration<URL> urls = classLoader.getResources("META-INF/persistence.xml");
        if (urls != null && urls.hasMoreElements()) {
            processImplicits = false;
            this.processPersistenceXmls(event,
                                        beanManager,
                                        classLoader,
                                        urls,
                                        providers,
                                        preexistingPersistenceUnitInfoBeans != null
                                        && !preexistingPersistenceUnitInfoBeans.isEmpty());
        }

        // If we did not find any PersistenceUnitInfo instances via
        // any other means, only then look at those defined "implicitly",
        // i.e. via type-level @PersistenceContext annotations.
        if (processImplicits) {
            this.processImplicitPersistenceUnits(event, providers);
        }

        // Add beans to support JPA.  In some cases, JTA must be
        // present (see JPA section 7.5, for example: "A
        // container-managed entity manager must be a JTA entity
        // manager.").
        this.addContainerManagedJpaBeans(event, beanManager);

        // Clear out no-longer-needed-or-used collections to save
        // memory.
        this.cdiTransactionScopedEntityManagerQualifiers.clear();
        this.containerManagedEntityManagerFactoryQualifiers.clear();
        this.implicitPersistenceUnits.clear();
        this.nonTransactionalEntityManagerQualifiers.clear();
        this.persistenceContextQualifiers.clear();
        this.persistenceUnitQualifiers.clear();
        this.unlistedManagedClassesByPersistenceUnitNames.clear();

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Ensures that {@link PersistenceUnitInfo}-typed injection points
     * are satisfied.
     *
     * @param event the {@link AfterDeploymentValidation} container
     * lifecycle event; must not be {@code null}
     *
     * @param beanManager the {@link BeanManager} currently in effect;
     * must not be {@code null}
     *
     * @exception NullPointerException if either {@code event} or
     * {@code beanManager} is {@code null}
     */
    private void validate(@Observes final AfterDeploymentValidation event, final BeanManager beanManager) {
        final String cn = JpaExtension.class.getName();
        final String mn = "validateJpaInjectionPoints";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, beanManager});
        }

        Objects.requireNonNull(event);
        Objects.requireNonNull(beanManager);

        if (this.defaultPersistenceUnitInEffect && !this.addedDefaultPersistenceUnit) {
            // The user had originally specified something like
            // just @PersistenceContext (instead
            // of @PersistenceContext(unitName = "something")), but
            // for whatever reason a default PersistenceUnitInfo bean
            // was not added.  This will only ever be the case if
            // multiple persistence units are present.
            final Set<Bean<?>> persistenceUnitInfoBeans = beanManager.getBeans(PersistenceUnitInfo.class, Any.Literal.INSTANCE);
            assert persistenceUnitInfoBeans != null;
            assert persistenceUnitInfoBeans.size() > 1 : "Unexpected persistenceUnitInfoBeans: " + persistenceUnitInfoBeans;
            try {
                beanManager.resolve(persistenceUnitInfoBeans);
            } catch (final AmbiguousResolutionException expected) {
                final Set<String> names = new HashSet<>();
                for (final Bean<?> bean : persistenceUnitInfoBeans) {
                    assert bean != null;
                    final Set<Annotation> qualifiers = bean.getQualifiers();
                    for (final Annotation qualifier : qualifiers) {
                        if (qualifier instanceof Named) {
                            names.add(((Named) qualifier).value());
                            break;
                        }
                    }
                }
                event.addDeploymentProblem(new AmbiguousResolutionException(Messages.format("ambiguousPersistenceUnitInfo",
                                                                                            names),
                                                                            expected));
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    /**
     * Adds certain beans to support injection of {@link
     * EntityManagerFactory} and {@link EntityManager} instances
     * according to the JPA specification.
     *
     * @param event an {@link AfterBeanDiscovery} container lifecycle
     * event; must not be {@code null}
     *
     * @param beanManager the current {@link BeanManager}; must not be
     * {@code null}
     *
     * @exception NullPointerException if either {@code event} or
     * {@code beanManager} is {@code null}
     *
     * @see
     * #addContainerManagedEntityManagerFactoryBeans(AfterBeanDiscovery,
     * Set, BeanManager)
     *
     * @see
     * #addCdiTransactionScopedEntityManagerBeans(AfterBeanDiscovery,
     * Set)
     *
     * @see #addExtendedEntityManagerBeans(AfterBeanDiscovery, Set,
     * BeanManager)
     *
     * @see #addNonTransactionalEntityManagerBeans(AfterBeanDiscovery,
     * Set, BeanManager)
     *
     * @see
     * #addJpaTransactionScopedEntityManagerBeans(AfterBeanDiscovery,
     * Set)
     */
    private void addContainerManagedJpaBeans(final AfterBeanDiscovery event, final BeanManager beanManager)
        throws ReflectiveOperationException {
        final String cn = JpaExtension.class.getName();
        final String mn = "addContainerManagedJpaBeans";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, beanManager});
        }

        Objects.requireNonNull(event);
        Objects.requireNonNull(beanManager);

        for (final Set<Annotation> qualifiers : this.persistenceUnitQualifiers) {
            addContainerManagedEntityManagerFactoryBeans(event, qualifiers, beanManager);
        }
        if (this.transactionsSupported) {
            for (final Set<Annotation> qualifiers : this.persistenceContextQualifiers) {
                // Note that each add* method invoked below is
                // responsible for ensuring that it adds beans only
                // once if at all, i.e. for validating and
                // de-duplicating the qualifiers that it is supplied
                // with if necessary.
                addContainerManagedEntityManagerFactoryBeans(event, qualifiers, beanManager);
                addCdiTransactionScopedEntityManagerBeans(event, qualifiers);
                if (qualifiers.contains(Extended.Literal.INSTANCE)) {
                    addExtendedEntityManagerBeans(event, qualifiers, beanManager);
                } else {
                    assert qualifiers.contains(JpaTransactionScoped.Literal.INSTANCE);
                    addNonTransactionalEntityManagerBeans(event, qualifiers, beanManager);
                    addJpaTransactionScopedEntityManagerBeans(event, qualifiers);
                }
            }
        } else {
            for (final Set<Annotation> qualifiers : this.persistenceContextQualifiers) {
                // Note that each add* method invoked below is
                // responsible for ensuring that it adds beans only
                // once if at all, i.e. for validating the qualifiers
                // that it is supplied with.
                addContainerManagedEntityManagerFactoryBeans(event, qualifiers, beanManager);
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private void addContainerManagedEntityManagerFactoryBeans(final AfterBeanDiscovery event,
                                                              final Set<Annotation> suppliedQualifiers,
                                                              final BeanManager beanManager) {
        final String cn = JpaExtension.class.getName();
        final String mn = "addContainerManagedEntityManagerFactoryBeans";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, suppliedQualifiers, beanManager});
        }

        Objects.requireNonNull(event);
        Objects.requireNonNull(suppliedQualifiers);
        Objects.requireNonNull(beanManager);

        // Provide support for, e.g.:
        //   @Inject
        //   @ContainerManaged
        //   @Named("test")
        //   private final EntityManagerFactory emf;
        final Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.removeAll(JpaCdiQualifiers.JPA_CDI_QUALIFIERS);
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        if (this.containerManagedEntityManagerFactoryQualifiers.add(qualifiers)) {
            event.addBean()
                .addType(EntityManagerFactory.class)
                .scope(ApplicationScoped.class)
                .addQualifiers(qualifiers)
                .produceWith(instance -> {
                        // On its own line to ease debugging.
                        return EntityManagerFactories.createContainerManagedEntityManagerFactory(instance,
                                                                                                 qualifiers,
                                                                                                 beanManager);
                    })
                .disposeWith((emf, instance) -> {
                        if (emf.isOpen()) {
                            emf.close();
                        }
                    });
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private void addCdiTransactionScopedEntityManagerBeans(final AfterBeanDiscovery event,
                                                           final Set<Annotation> suppliedQualifiers)
        throws ReflectiveOperationException {
        final String cn = JpaExtension.class.getName();
        final String mn = "addCdiTransactionScopedEntityManagerBeans";
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
        //   @CdiTransactionScoped
        //   @Synchronized // <-- NOTE
        //   @Named("test")
        //   private final EntityManager cdiTransactionScopedEm;
        //
        // ...AND:
        //
        //   @Inject
        //   @ContainerManaged
        //   @CdiTransactionScoped
        //   @Unsynchronized // <-- NOTE
        //   @Named("test")
        //   private final EntityManager cdiTransactionScopedEm;
        final Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        qualifiers.add(CdiTransactionScoped.Literal.INSTANCE);
        qualifiers.remove(Extended.Literal.INSTANCE);
        qualifiers.remove(JpaTransactionScoped.Literal.INSTANCE);
        qualifiers.remove(NonTransactional.Literal.INSTANCE);
        qualifiers.remove(Synchronized.Literal.INSTANCE);
        qualifiers.remove(Unsynchronized.Literal.INSTANCE);
        if (!this.cdiTransactionScopedEntityManagerQualifiers.contains(qualifiers)) {
            this.cdiTransactionScopedEntityManagerQualifiers.add(new HashSet<>(qualifiers));
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

            qualifiers.add(Synchronized.Literal.INSTANCE);
            final Set<Annotation> synchronizedQualifiers = new HashSet<>(qualifiers);
            event.<CdiTransactionScopedEntityManager>addBean()
                .addTransitiveTypeClosure(CdiTransactionScopedEntityManager.class)
                .scope(scope)
                .addQualifiers(synchronizedQualifiers)
                .produceWith(instance -> {
                        // On its own line to ease debugging.
                        return new CdiTransactionScopedEntityManager(instance, synchronizedQualifiers);
                    })
                .disposeWith(CdiTransactionScopedEntityManager::dispose);

            qualifiers.remove(Synchronized.Literal.INSTANCE);
            qualifiers.add(Unsynchronized.Literal.INSTANCE);
            final Set<Annotation> unsynchronizedQualifiers = new HashSet<>(qualifiers);
            event.<CdiTransactionScopedEntityManager>addBean()
                .addTransitiveTypeClosure(CdiTransactionScopedEntityManager.class)
                .scope(scope)
                .addQualifiers(unsynchronizedQualifiers)
                .produceWith(instance -> {
                        // On its own line to ease debugging.
                        return new CdiTransactionScopedEntityManager(instance, unsynchronizedQualifiers);
                    })
                .disposeWith(CdiTransactionScopedEntityManager::dispose);
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private void addJpaTransactionScopedEntityManagerBeans(final AfterBeanDiscovery event,
                                                           final Set<Annotation> suppliedQualifiers) {
        final String cn = JpaExtension.class.getName();
        final String mn = "addJpaTransactionScopedEntityManagerBeans";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, suppliedQualifiers});
        }

        Objects.requireNonNull(event);
        Objects.requireNonNull(suppliedQualifiers);
        if (!this.transactionsSupported) {
            throw new IllegalStateException();
        }

        // The JpaTransactionScopedEntityManager "tunnels" another
        // scope through it.

        // Provide support for, e.g.:
        //   @Inject
        //   @ContainerManaged
        //   @JpaTransactionScoped
        //   @Synchronized // or @Unsynchronized, or none
        //   @Named("test")
        //   private final EntityManager jpaTransactionScopedEm;
        final Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        qualifiers.add(JpaTransactionScoped.Literal.INSTANCE);
        qualifiers.remove(CdiTransactionScoped.Literal.INSTANCE);
        qualifiers.remove(Extended.Literal.INSTANCE);
        qualifiers.remove(NonTransactional.Literal.INSTANCE);
        event.<JpaTransactionScopedEntityManager>addBean()
            .addTransitiveTypeClosure(JpaTransactionScopedEntityManager.class)
            .scope(Dependent.class)
            .addQualifiers(qualifiers)
            .produceWith(instance -> {
                    // On its own line to ease debugging.
                    return new JpaTransactionScopedEntityManager(instance, suppliedQualifiers);
                })
            .disposeWith(JpaTransactionScopedEntityManager::dispose);


        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private void addNonTransactionalEntityManagerBeans(final AfterBeanDiscovery event,
                                                       final Set<Annotation> suppliedQualifiers,
                                                       final BeanManager beanManager) {
        final String cn = JpaExtension.class.getName();
        final String mn = "addNonTransactionalEntityManagerBeans";
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
        if (this.nonTransactionalEntityManagerQualifiers.add(qualifiers)) {
            event.<NonTransactionalEntityManager>addBean()
                .addTransitiveTypeClosure(NonTransactionalEntityManager.class)
                .scope(ReferenceCounted.class)
                .addQualifiers(qualifiers)
                .produceWith(instance -> {
                        // On its own line to ease debugging.
                        return new NonTransactionalEntityManager(instance, suppliedQualifiers);
                    })
                // Revisit: ReferenceCountedContext does not
                // automatically pick up synthetic beans like this
                // one.  So we have to tell it somehow to "work on"
                // this bean.  Right now this bean is in what amounts
                // to a thread-specific singleton scope.  As it
                // happens, this might actually be OK.
                .disposeWith((em, instance) -> {
                        if (em.isOpen()) {
                            em.close();
                        }
                    });
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private void addExtendedEntityManagerBeans(final AfterBeanDiscovery event,
                                               final Set<Annotation> suppliedQualifiers,
                                               final BeanManager beanManager) {
        final String cn = JpaExtension.class.getName();
        final String mn = "addExtendedEntityManagerBeans";
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
        //   private final EntityManager extendedEm;
        final Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        qualifiers.add(Extended.Literal.INSTANCE);
        qualifiers.remove(JpaTransactionScoped.Literal.INSTANCE);
        qualifiers.remove(CdiTransactionScoped.Literal.INSTANCE);
        qualifiers.remove(NonTransactional.Literal.INSTANCE);
        event.<ExtendedEntityManager>addBean()
            .addTransitiveTypeClosure(ExtendedEntityManager.class)
            .scope(ReferenceCounted.class)
            .qualifiers(qualifiers)
            .produceWith(instance -> {
                    // On its own line to ease debugging.
                    return new ExtendedEntityManager(instance, suppliedQualifiers, beanManager);
                })
            .disposeWith((em, instance) -> em.closeDelegates());

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

        int persistenceUnitCount = 0;
        PersistenceUnitInfoBean solePersistenceUnitInfoBean = null;
        for (final PersistenceUnitInfoBean persistenceUnitInfoBean : this.implicitPersistenceUnits.values()) {
            assert persistenceUnitInfoBean != null;
            String persistenceUnitName = persistenceUnitInfoBean.getPersistenceUnitName();
            if (persistenceUnitName == null || persistenceUnitName.isEmpty()) {
                persistenceUnitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                this.defaultPersistenceUnitInEffect = true;
            }
            if (!persistenceUnitInfoBean.excludeUnlistedClasses()) {
                final Collection<? extends Class<?>> unlistedManagedClasses =
                    this.unlistedManagedClassesByPersistenceUnitNames.get(persistenceUnitName);
                if (unlistedManagedClasses != null && !unlistedManagedClasses.isEmpty()) {
                    for (final Class<?> unlistedManagedClass : unlistedManagedClasses) {
                        persistenceUnitInfoBean.addManagedClassName(unlistedManagedClass.getName());
                    }
                }
            }

            // Provide support for, e.g.:
            //   @Inject
            //   @Named("test")
            //   private PersistenceUnitInfo persistenceUnitInfo;
            event.addBean()
                .beanClass(PersistenceUnitInfoBean.class)
                .types(Collections.singleton(PersistenceUnitInfo.class))
                .scope(ApplicationScoped.class)
                .addQualifiers(NamedLiteral.of(persistenceUnitName))
                .createWith(cc -> persistenceUnitInfoBean);
            if (persistenceUnitCount == 0) {
                assert solePersistenceUnitInfoBean == null;
                solePersistenceUnitInfoBean = persistenceUnitInfoBean;
            } else if (solePersistenceUnitInfoBean != null) {
                solePersistenceUnitInfoBean = null;
            }
            maybeAddPersistenceProviderBean(event, persistenceUnitInfoBean, providers);
            persistenceUnitCount++;
        }
        switch (persistenceUnitCount) {
        case 0:
            break;

        case 1:
            assert solePersistenceUnitInfoBean != null;
            final String name = solePersistenceUnitInfoBean.getPersistenceUnitName();
            if (name != null && !name.isEmpty()) {
                this.defaultPersistenceUnitInEffect = true;
                this.addedDefaultPersistenceUnit = true;
                final PersistenceUnitInfoBean instance = solePersistenceUnitInfoBean;
                event.addBean()
                    .beanClass(PersistenceUnitInfoBean.class)
                    .types(Collections.singleton(PersistenceUnitInfo.class))
                    .scope(ApplicationScoped.class)
                    .addQualifiers(NamedLiteral.of(DEFAULT_PERSISTENCE_UNIT_NAME))
                    .createWith(cc -> instance);
            }
            break;

        default:
            assert persistenceUnitCount > 1;
            assert solePersistenceUnitInfoBean == null
                : "Unexpected solePersistenceUnitInfoBean: " + solePersistenceUnitInfoBean
                + " with persistenceUnitCount " + persistenceUnitCount;
            break;
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private void processPersistenceXmls(final AfterBeanDiscovery event,
                                        final BeanManager beanManager,
                                        final ClassLoader classLoader,
                                        final Enumeration<URL> urls,
                                        final Collection<? extends PersistenceProvider> providers,
                                        final boolean userSuppliedPersistenceUnitInfoBeans)
        throws IOException, JAXBException, ReflectiveOperationException, XMLStreamException {
        final String cn = JpaExtension.class.getName();
        final String mn = "processPersistenceXmls";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, new Object[] {event, beanManager, classLoader, urls, providers});
        }

        Objects.requireNonNull(event);

        if (urls != null && urls.hasMoreElements()) {
            final Supplier<? extends ClassLoader> tempClassLoaderSupplier = () -> {
                if (classLoader instanceof URLClassLoader) {
                    return new URLClassLoader(((URLClassLoader) classLoader).getURLs());
                } else {
                    return classLoader;
                }
            };

            // We use StAX for XML loading because it is the same XML
            // parsing strategy used by all known CDI implementations.
            // If the end user wants to customize the StAX
            // implementation then we want that customization to apply
            // here as well.
            //
            // Note that XMLInputFactory is NOT deprecated in JDK 8:
            //   https://docs.oracle.com/javase/8/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory--
            // ...but IS deprecated in JDK 9:
            //   https://docs.oracle.com/javase/9/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory--
            // ...with an incorrect claim that it was deprecated since
            // JDK 1.7.  In JDK 7 it actually was *not* deprecated:
            //   https://docs.oracle.com/javase/7/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory()
            // ...and in JDK 10 it is NO LONGER deprecated:
            //   https://docs.oracle.com/javase/10/docs/api/javax/xml/stream/XMLInputFactory.html#newFactory()
            // ...nor is it deprecated in JDK 11:
            //   https://docs.oracle.com/en/java/javase/11/docs/api/java.xml/javax/xml/stream/XMLInputFactory.html#newFactory()
            // ...nor in JDK 12:
            //   https://docs.oracle.com/en/java/javase/12/docs/api/java.xml/javax/xml/stream/XMLInputFactory.html#newFactory()
            // So we suppress deprecation warnings since deprecation
            // in JDK 9 appears to have been a mistake.
            @SuppressWarnings("deprecation")
            final XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();

            // See
            // https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.md#xmlinputfactory-a-stax-parser
            xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            xmlInputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);

            final Unmarshaller unmarshaller =
                JAXBContext.newInstance(Persistence.class.getPackage().getName()).createUnmarshaller();
            final Supplier<? extends DataSourceProvider> dataSourceProviderSupplier =
              () -> beanManager.createInstance().select(DataSourceProvider.class).get();
            PersistenceUnitInfo solePersistenceUnitInfo = null;
            while (urls.hasMoreElements()) {
                final URL url = urls.nextElement();
                assert url != null;
                Collection<PersistenceUnitInfo> persistenceUnitInfos = null;
                Persistence persistence = null;
                try (InputStream inputStream = new BufferedInputStream(url.openStream())) {
                    final XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(inputStream);
                    try {
                        persistence = (Persistence) unmarshaller.unmarshal(reader);
                    } finally {
                        reader.close();
                    }
                }
                final Collection<? extends Persistence.PersistenceUnit> persistenceUnits = persistence.getPersistenceUnit();
                if (persistenceUnits != null && !persistenceUnits.isEmpty()) {
                    persistenceUnitInfos = new ArrayList<>();
                    for (final Persistence.PersistenceUnit persistenceUnit : persistenceUnits) {
                        if (persistenceUnit != null) {
                            persistenceUnitInfos
                                .add(PersistenceUnitInfoBean.fromPersistenceUnit(persistenceUnit,
                                                                                 classLoader,
                                                                                 tempClassLoaderSupplier,
                                                                                 new URL(url, ".."), // i.e. META-INF/..
                                                                                 unlistedManagedClassesByPersistenceUnitNames,
                                                                                 dataSourceProviderSupplier));
                        }
                    }
                }
                if (persistenceUnitInfos != null && !persistenceUnitInfos.isEmpty()) {
                    for (final PersistenceUnitInfo persistenceUnitInfo : persistenceUnitInfos) {
                        String persistenceUnitName = persistenceUnitInfo.getPersistenceUnitName();
                        if (persistenceUnitName == null || persistenceUnitName.isEmpty()) {
                            persistenceUnitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                            this.defaultPersistenceUnitInEffect = true;
                        }
                        // Provide support for, e.g.:
                        //   @Inject
                        //   @Named("test")
                        //   private PersistenceUnitInfo persistenceUnitInfo;
                        event.addBean()
                            .beanClass(PersistenceUnitInfoBean.class)
                            .types(Collections.singleton(PersistenceUnitInfo.class))
                            .scope(ApplicationScoped.class)
                            .addQualifiers(NamedLiteral.of(persistenceUnitName))
                            .createWith(cc -> persistenceUnitInfo);
                        if (solePersistenceUnitInfo == null) {
                          solePersistenceUnitInfo = persistenceUnitInfo;
                        } else {
                          solePersistenceUnitInfo = null;
                        }
                        maybeAddPersistenceProviderBean(event, persistenceUnitInfo, providers);
                    }
                }
            }
            if (!userSuppliedPersistenceUnitInfoBeans && solePersistenceUnitInfo != null) {
                final String name = solePersistenceUnitInfo.getPersistenceUnitName();
                if (name != null && !name.isEmpty() && !name.equals(DEFAULT_PERSISTENCE_UNIT_NAME)) {
                    this.defaultPersistenceUnitInEffect = true;
                    this.addedDefaultPersistenceUnit = true;
                    final PersistenceUnitInfo instance = solePersistenceUnitInfo;
                    event.addBean()
                        .beanClass(PersistenceUnitInfoBean.class)
                        .types(Collections.singleton(PersistenceUnitInfo.class))
                        .scope(ApplicationScoped.class)
                        .addQualifiers(NamedLiteral.of(DEFAULT_PERSISTENCE_UNIT_NAME))
                        .createWith(cc -> instance);
                }
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
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
    private <T> void rewritePersistenceContextFieldAnnotations(final AnnotatedFieldConfigurator<T> fc) {
        final String cn = JpaExtension.class.getName();
        final String mn = "rewritePersistenceContextFieldAnnotations";
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.entering(cn, mn, fc);
        }

        Objects.requireNonNull(fc);

        final PersistenceContext pc = fc.getAnnotated().getAnnotation(PersistenceContext.class);
        if (pc != null) {
            fc.remove(a -> a == pc);
            fc.add(InjectLiteral.INSTANCE);
            fc.add(ContainerManaged.Literal.INSTANCE);
            if (PersistenceContextType.EXTENDED.equals(pc.type())) {
                fc.add(Extended.Literal.INSTANCE);
            } else {
                fc.add(JpaTransactionScoped.Literal.INSTANCE);
            }
            if (SynchronizationType.UNSYNCHRONIZED.equals(pc.synchronization())) {
                fc.add(Unsynchronized.Literal.INSTANCE);
            } else {
                fc.add(Synchronized.Literal.INSTANCE);
            }
            if (LOGGER.isLoggable(Level.INFO)) {
                final String name = pc.name().trim();
                if (!name.isEmpty()) {
                    LOGGER.logp(Level.INFO, cn, mn, "persistenceContextNameIgnored", new Object[] {fc.getAnnotated(), name});
                }
            }
            String unitName = pc.unitName().trim();
            if (unitName.isEmpty()) {
                unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                this.defaultPersistenceUnitInEffect = true;
            }
            fc.add(NamedLiteral.of(unitName));
        }

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
    private <T> void rewritePersistenceUnitFieldAnnotations(final AnnotatedFieldConfigurator<T> fc) {
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
        String unitName = pu.unitName().trim();
        if (unitName.isEmpty()) {
            unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
            this.defaultPersistenceUnitInEffect = true;
        }
        fc.add(NamedLiteral.of(unitName));

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

    private <T> void rewritePersistenceContextSetterMethodAnnotations(final AnnotatedMethodConfigurator<T> mc) {
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
                if (LOGGER.isLoggable(Level.INFO)) {
                    final String name = pc.name().trim();
                    if (!name.isEmpty()) {
                        LOGGER.logp(Level.INFO, cn, mn, "persistenceContextNameIgnored", new Object[] {annotated, name});
                    }
                }
                boolean observerMethod = false;
                final List<AnnotatedParameterConfigurator<T>> parameters = mc.params();
                if (parameters != null && !parameters.isEmpty()) {
                    for (final AnnotatedParameterConfigurator<T> apc : parameters) {
                        final Annotated parameter = apc.getAnnotated();
                        if (parameter.isAnnotationPresent(Observes.class)) {
                            if (!observerMethod) {
                                observerMethod = true;
                            }
                        } else {
                            final Type parameterType = parameter.getBaseType();
                            if (parameterType instanceof Class
                                && EntityManager.class.isAssignableFrom((Class<?>) parameterType)) {
                                apc.add(ContainerManaged.Literal.INSTANCE);
                                if (PersistenceContextType.EXTENDED.equals(pc.type())) {
                                    apc.add(Extended.Literal.INSTANCE);
                                } else {
                                    apc.add(JpaTransactionScoped.Literal.INSTANCE);
                                }
                                if (SynchronizationType.UNSYNCHRONIZED.equals(pc.synchronization())) {
                                    apc.add(Unsynchronized.Literal.INSTANCE);
                                } else {
                                    apc.add(Synchronized.Literal.INSTANCE);
                                }
                                String unitName = pc.unitName().trim();
                                if (unitName.isEmpty()) {
                                    unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                                    this.defaultPersistenceUnitInEffect = true;
                                }
                                apc.add(NamedLiteral.of(unitName));
                            }
                        }
                    }
                    mc.remove(a -> a == pc);
                    if (!observerMethod) {
                        mc.add(InjectLiteral.INSTANCE);
                    }
                }
            }
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private <T> void rewritePersistenceUnitSetterMethodAnnotations(final AnnotatedMethodConfigurator<T> mc) {
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
                        final Annotated parameter = apc.getAnnotated();
                        if (parameter.isAnnotationPresent(Observes.class)) {
                            if (!observerMethod) {
                                observerMethod = true;
                            }
                        } else {
                            final Type parameterType = parameter.getBaseType();
                            if (parameterType instanceof Class
                                && EntityManagerFactory.class.isAssignableFrom((Class<?>) parameterType)) {
                                apc.add(ContainerManaged.Literal.INSTANCE);
                                String unitName = pu.unitName().trim();
                                if (unitName.isEmpty()) {
                                    unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                                    this.defaultPersistenceUnitInEffect = true;
                                }
                                apc.add(NamedLiteral.of(unitName));
                            }
                        }
                    }
                    mc.remove(a -> a == pu);
                    if (!observerMethod) {
                        mc.add(InjectLiteral.INSTANCE);
                    }
                }
            }

        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.exiting(cn, mn);
        }
    }

    private void maybeAddPersistenceProviderBeans(final AfterBeanDiscovery event,
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
                    this.maybeAddPersistenceProviderBean(event, pui, providers);
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

        // Provide support for, e.g.:
        //   @Inject
        //   private PersistenceProviderResolver ppr;
        event.addBean()
            .types(PersistenceProviderResolver.class)
            .scope(ApplicationScoped.class)
            .createWith(cc -> resolver);
        final Collection<? extends PersistenceProvider> providers = resolver.getPersistenceProviders();
        for (final PersistenceProvider provider : providers) {
            // Provide support for, e.g.:
            //   @Inject
            //   private MyPersistenceProviderSubclassMaybeFromPersistenceXml ppr;
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
     * <code>PersistenceUnitInfo</code>} if the supplied {@link
     * Collection} of {@link PersistenceProvider}s does not contain
     * an instance of it.
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
    private void maybeAddPersistenceProviderBean(final AfterBeanDiscovery event,
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
                String persistenceUnitName = persistenceUnitInfo.getPersistenceUnitName();
                if (persistenceUnitName == null || persistenceUnitName.isEmpty()) {
                    persistenceUnitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                    this.defaultPersistenceUnitInEffect = true;
                }

                // Provide support for, e.g.:
                //   @Inject
                //   @Named("test")
                //   private PersistenceProvider providerProbablyReferencedFromAPersistenceXml;
                event.addBean()
                    .types(PersistenceProvider.class)
                    .scope(ApplicationScoped.class)
                    .addQualifiers(NamedLiteral.of(persistenceUnitName))
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

    private static void onStartup(@Observes
                                  @Initialized(ApplicationScoped.class)
                                  @Priority(LIBRARY_BEFORE)
                                  final Object event,
                                  @ContainerManaged
                                  final Instance<EntityManagerFactory> emfs) {
        if (!emfs.isUnsatisfied()) {
            for (final EntityManagerFactory emfProxy : emfs) {
                // Container-managed EntityManagerFactory instances
                // are client proxies, so we call a business method to
                // force "inflation" of the proxied instance.  This,
                // in turn, may run DDL and persistence provider
                // validation if the persistence provider has been
                // configured to do such things early (like
                // Eclipselink with its eclipselink.deploy-on-startup
                // property).
                emfProxy.isOpen();
            }
        }
    }

}
