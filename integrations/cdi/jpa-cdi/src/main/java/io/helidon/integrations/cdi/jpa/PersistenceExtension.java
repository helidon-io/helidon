/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import io.helidon.integrations.cdi.jpa.PersistenceUnitInfoBean.DataSourceProvider;
import io.helidon.integrations.cdi.jpa.jaxb.Persistence;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.InjectionException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Vetoed;
import jakarta.enterprise.inject.literal.InjectLiteral;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterTypeDiscovery;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedCallable;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessBeanAttributes;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.inject.spi.configurator.AnnotatedFieldConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.persistence.Converter;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceProperty;
import jakarta.persistence.PersistenceUnit;
import jakarta.persistence.SynchronizationType;
import jakarta.persistence.spi.PersistenceProvider;
import jakarta.persistence.spi.PersistenceProviderResolver;
import jakarta.persistence.spi.PersistenceProviderResolverHolder;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_AFTER;
import static jakarta.persistence.PersistenceContextType.EXTENDED;
import static jakarta.persistence.SynchronizationType.SYNCHRONIZED;
import static jakarta.persistence.SynchronizationType.UNSYNCHRONIZED;

/**
 * An {@link Extension} that integrates <em>container-mode</em> <a
 * href="https://jakarta.ee/specifications/persistence/3.0/jakarta-persistence-spec-3.0.html">Jakarta Persistence
 * 3.0</a> into <a href="https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html">CDI SE 3.0</a>-based
 * applications.
 */
public final class PersistenceExtension implements Extension {


    /*
     * Static fields.
     */


    private static final TypeLiteral<Bean<EntityManagerFactory>> BEAN_ENTITYMANAGERFACTORY_TYPELITERAL = new TypeLiteral<>() {};

    private static final TypeLiteral<Bean<JtaExtendedEntityManager>> BEAN_JTAEXTENDEDENTITYMANAGER_TYPELITERAL =
        new TypeLiteral<>() {};

    private static final TypeLiteral<Bean<JtaEntityManager>> BEAN_JTAENTITYMANAGER_TYPELITERAL = new TypeLiteral<>() {};

    /**
     * The name used to designate the only persistence unit in the environment, when there is exactly one persistence
     * unit in the environment, and there is at least one {@link PersistenceContext @PersistenceContext}-annotated
     * injection point that does not specify a value for the {@link PersistenceContext#unitName() unitName} element.
     *
     * <p>In such a case, the injection point will be effectively rewritten such that it will appear to the CDI
     * container as though there <em>were</em> a value specified for the {@link PersistenceContext#unitName() unitName}
     * element&mdash;namely this field's value.  Additionally, a bean identical to the existing solitary {@link
     * PersistenceUnitInfo}-typed bean will be added with this field's value as the {@linkplain Named#value() value of
     * its <code>Named</code> qualifier}, thus serving as a kind of alias for the "real" bean.</p>
     *
     * <p>This is necessary because the empty string ({@code ""}) as the value of the {@link Named#value()} element can
     * have <a href="https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#default_name">special
     * semantics</a>, so cannot be used to designate an unnamed or otherwise default persistence unit.</p>
     *
     * <p>The value of this field is subject to change without prior notice at any point.  In general the mechanics
     * around injection point rewriting are also subject to change without prior notice at any point.</p>
     */
    static final String DEFAULT_PERSISTENCE_UNIT_NAME = "__DEFAULT__";

    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];

    private static final TypeLiteral<Event<PersistenceUnitInfoBean>> EVENT_PERSISTENCEUNITINFOBEAN_TYPELITERAL =
        new TypeLiteral<>() {};

    private static final Logger LOGGER = Logger.getLogger(PersistenceExtension.class.getName());


    /*
     * Instance fields.
     */


    /**
     * Whether or not this extension will do anything.
     *
     * <p>This field's value is {@code true} by default.</p>
     */
    private final boolean enabled;

    /**
     * A {@link Map} of {@link PersistenceUnitInfoBean} instances that were created by the {@link
     * #gatherImplicitPersistenceUnits(ProcessAnnotatedType, BeanManager)} observer method, indexed by the names of
     * persistence units.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>The contents of this field are used only when no explicit {@link PersistenceUnitInfo} beans are otherwise
     * available in the container.</p>
     *
     * <p>After this portable extension finishes processing, this {@link Map} will be {@linkplain Map#isEmpty()
     * empty}.</p>
     *
     * @see #gatherImplicitPersistenceUnits(ProcessAnnotatedType, BeanManager)
     */
    private final Map<String, PersistenceUnitInfoBean> implicitPersistenceUnits;

    /**
     * A {@link Map} of {@link Set}s of {@link Class}es whose keys are persistence unit names and whose values are
     * {@link Set}s of {@link Class}es discovered by CDI (and hence consist of unlisted classes in the sense that they
     * might not be found in any {@link PersistenceUnitInfo}).
     *
     * <p>After this portable extension finishes processing, this {@link Map} will be {@linkplain Map#isEmpty()
     * empty}.</p>
     *
     * <p>Such {@link Class}es, of course, might not have been weaved appropriately by the relevant {@link
     * PersistenceProvider}.</p>
     *
     * <p>This field is never {@code null}.</p>
     */
    private final Map<String, Set<Class<?>>> unlistedManagedClassesByUnitNames;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers annotating CDI injection points related to JPA.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>These qualifiers are built up as this portable extension {@linkplain ProcessInjectionPoint discovers {@code
     * EntityManager}-typed <code>InjectionPoint</code>s}.  After this portable extension finishes processing, this
     * {@link Set} will be {@linkplain Set#isEmpty() empty}.</p>
     *
     * @see #saveContainerManagedEntityManagerQualifiers(ProcessInjectionPoint)
     */
    private final Set<Set<Annotation>> containerManagedEntityManagerQualifiers;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers for which container-managed {@link EntityManagerFactory} beans
     * may be created.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>These qualifiers are built up as this portable extension {@linkplain ProcessInjectionPoint discovers {@code
     * EntityManagerFactory}-typed <code>InjectionPoint</code>s}.  After this portable extension finishes processing,
     * this {@link Set} will be {@linkplain Set#isEmpty() empty}.</p>
     *
     * @see #saveContainerManagedEntityManagerFactoryQualifiers(ProcessInjectionPoint)
     */
    private final Set<Set<Annotation>> entityManagerFactoryQualifiers;

    /**
     * Indicates if JTA transactions can be supported.
     *
     * @see #addAllocatorBeanAndInstallTransactionSupport(AfterTypeDiscovery)
     */
    private boolean transactionsSupported;

    /**
     * A {@link Set} of {@link Set}s of CDI qualifiers that serves as a kind of cache, preventing more than one {@link
     * ContainerManaged}-qualified {@link EntityManagerFactory}-typed bean from being added for the same set of
     * qualifiers.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>After this portable extension finishes processing, this {@link Set} will be {@linkplain Set#isEmpty()
     * empty}.</p>
     *
     * @see #addContainerManagedEntityManagerFactoryBeans(AfterBeanDiscovery, Set)
     */
    private final Set<Set<Annotation>> containerManagedEntityManagerFactoryQualifiers;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link PersistenceExtension}.
     *
     * @deprecated For invocation by CDI only.
     */
    @Deprecated // For invocation by CDI only.
    public PersistenceExtension() {
        super();
        this.enabled = Boolean.parseBoolean(System.getProperty(this.getClass().getName() + ".enabled", "false"));
        this.containerManagedEntityManagerQualifiers = new HashSet<>();
        this.containerManagedEntityManagerFactoryQualifiers = new HashSet<>();
        this.entityManagerFactoryQualifiers = new HashSet<>();
        this.implicitPersistenceUnits = new HashMap<>();
        this.transactionsSupported = false;
        this.unlistedManagedClassesByUnitNames = new HashMap<>();
    }


    /*
     * Observer methods.
     */


    private <T> void addTypes(@Observes AfterTypeDiscovery event) {
        if (!this.enabled) {
            return;
        }
        event.addAnnotatedType(ReferenceCountingProducer.class, ReferenceCountingProducer.class.getName());
        try {
            Class.forName("jakarta.transaction.TransactionSynchronizationRegistry",
                          false,
                          Thread.currentThread().getContextClassLoader());
            event.addAnnotatedType(JtaTransactionRegistry.class, JtaTransactionRegistry.class.getName());
            event.addAnnotatedType(JtaAdaptingDataSourceProvider.class, JtaAdaptingDataSourceProvider.class.getName());
            this.transactionsSupported = true;
        } catch (ClassNotFoundException e) {
            event.addAnnotatedType(NoTransactionRegistry.class, NoTransactionRegistry.class.getName());
            event.addAnnotatedType(JtaAbsentDataSourceProvider.class, JtaAbsentDataSourceProvider.class.getName());
            this.transactionsSupported = false;
        }
    }

    private <T> void makePersistencePropertyARepeatableQualifier(@Observes BeforeBeanDiscovery event) {
        if (!this.enabled) {
            return;
        }
        event.addQualifier(PersistenceProperty.class);
    }

    /**
     * {@linkplain ProcessBeanAttributes#veto() Vetoes} any bean whose bean types includes the deprecated {@link
     * JtaDataSourceProvider} class, since it is replaced by {@link JtaAdaptingDataSourceProvider}.
     *
     * @param event the {@link ProcessBeanAttributes} event in question; must not be {@code null}
     *
     * @exception NullPointerException if {@code event} is {@code null}
     *
     * @see JtaAdaptingDataSourceProvider
     */
    private void vetoDeprecatedJtaDataSourceProvider(@Observes ProcessBeanAttributes<JtaDataSourceProvider> event) {
        if (!this.enabled) {
            return;
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.logp(Level.FINE, this.getClass().getName(), "vetoDeprecatedJtaDataSourceProvider",
                        "Vetoing BeanAttributes {0} representing "
                        + JtaDataSourceProvider.class
                        + " because it is deprecated and "
                        + JtaAdaptingDataSourceProvider.class
                        + " replaces it", event.getBeanAttributes());
        }
        event.veto();
    }

    private <T> void rewriteJpaAnnotations(@Observes
                                           @WithAnnotations({PersistenceContext.class, PersistenceUnit.class})
                                           ProcessAnnotatedType<T> event) {
        if (!this.enabled) {
            return;
        }
        AnnotatedTypeConfigurator<T> atc = event.configureAnnotatedType();
        atc.filterFields(PersistenceExtension::isEligiblePersistenceContextAnnotated)
            .forEach(this::rewritePersistenceContextFieldAnnotations);
        atc.filterFields(PersistenceExtension::isEligiblePersistenceUnitAnnotated)
            .forEach(this::rewritePersistenceUnitFieldAnnotations);
        atc.filterMethods(PersistenceExtension::isEligiblePersistenceContextAnnotated)
            .forEach(this::rewritePersistenceContextInitializerMethodAnnotations);
        atc.filterMethods(PersistenceExtension::isEligiblePersistenceUnitAnnotated)
            .forEach(this::rewritePersistenceUnitInitializerMethodAnnotations);
    }

    /**
     * Looks for type-level {@link PersistenceContext} annotations that have at least one {@link PersistenceProperty}
     * annotation {@linkplain PersistenceContext#properties() associated with} them and uses them to define persistence
     * units, potentially preventing the need for {@code META-INF/persistence.xml} processing.
     *
     * @param event the {@link ProcessAnnotatedType} event occurring; must not be {@code null}
     *
     * @param bm the {@link BeanManager} in effect; must not be {@code null}
     *
     * @exception NullPointerException if either {@code event} or {@code bm} is {@code null}
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
                                                ProcessAnnotatedType<?> event,
                                                BeanManager bm) {
        if (!this.enabled) {
            return;
        }
        AnnotatedType<?> at = event.getAnnotatedType();
        if (at.isAnnotationPresent(Vetoed.class)) {
            return;
        }
        for (PersistenceContext pc : at.getAnnotations(PersistenceContext.class)) {
            PersistenceProperty[] pps = pc.properties();
            if (pps.length > 0) {
                String unitName = pc.unitName();
                if (unitName.isBlank()) {
                    unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                }
                PersistenceUnitInfoBean pui = this.implicitPersistenceUnits.get(unitName);
                if (pui == null) {
                    Properties properties = new Properties();
                    for (PersistenceProperty pp : pps) {
                        String ppName = pp.name();
                        if (!ppName.isBlank()) {
                            properties.setProperty(ppName, pp.value());
                        }
                    }
                    pui = new PersistenceUnitInfoBean(unitName,
                                                      locationOf(at),
                                                      null,
                                                      () -> bm.createInstance().select(DataSourceProvider.class).get(),
                                                      properties);
                    this.implicitPersistenceUnits.put(unitName, pui);
                }
            }
        }
    }

    /**
     * Tracks {@linkplain Converter converters}, {@linkplain Entity entities}, {@linkplain Embeddable embeddables} and
     * {@linkplain MappedSuperclass mapped superclasses} that were auto-discovered by CDI bean discovery, and makes sure
     * that they are not actually CDI beans, since according to the JPA specification they cannot be.
     *
     * <p>This method also keeps track of these classes as potential "unlisted classes" to be used by a {@linkplain
     * PersistenceUnitInfo persistence unit} if its {@linkplain PersistenceUnitInfo#excludeUnlistedClasses()} method
     * returns {@code false}.</p>
     *
     * @param event the event describing the {@link AnnotatedType} being processed; must not be {@code null}
     *
     * @exception NullPointerException if {@code event} is {@code null}
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
                                        ProcessAnnotatedType<?> event) {
        if (!this.enabled) {
            return;
        }
        AnnotatedType<?> at = event.getAnnotatedType();
        if (!at.isAnnotationPresent(Vetoed.class)) {
            this.assignManagedClassToPersistenceUnit(at.getAnnotations(PersistenceContext.class),
                                                     at.getAnnotations(PersistenceUnit.class),
                                                     at.getJavaClass());
        }
        event.veto(); // managed classes can't be beans
    }

    /**
     * Stores {@link Set}s of qualifiers that annotate container-managed {@link EntityManagerFactory}-typed injection
     * points.
     *
     * <p>{@link EntityManagerFactory}-typed beans will be added for each such valid {@link Set}.</p>
     *
     * @param e a {@link ProcessInjectionPoint} container lifecycle event; must not be {@code null}
     *
     * @exception NullPointerException if {@code e} is {@code null}
     */
    @SuppressWarnings("checkstyle:LineLength")
    private <T extends EntityManagerFactory> void saveContainerManagedEntityManagerFactoryQualifiers(@Observes ProcessInjectionPoint<?, T> e) {
        if (!this.enabled) {
            return;
        }
        boolean add = false;
        Set<Annotation> qualifiers = e.getInjectionPoint().getQualifiers();
        for (Annotation qualifier : qualifiers) {
            if (qualifier == ContainerManaged.Literal.INSTANCE) {
                if (!add) {
                    add = true;
                }
            } else if (SyntheticJpaQualifiers.INSTANCE.contains(qualifier)) {
                if (add) {
                    add = false;
                }
                e.addDefinitionError(new InjectionException("Invalid injection point; reserved qualifier used: " + qualifier));
            }
        }
        if (add) {
            this.entityManagerFactoryQualifiers.add(qualifiers);
        }
    }

    /**
     * Stores {@link Set}s of qualifiers that annotate {@link EntityManager}-typed injection points.
     *
     * <p>{@link EntityManager}-typed beans will be added for each such {@link Set}.</p>
     *
     * @param e a {@link ProcessInjectionPoint} container lifecycle event; must not be {@code null}
     *
     * @exception NullPointerException if {@code e} is {@code null}
     */
    private <T extends EntityManager> void saveContainerManagedEntityManagerQualifiers(@Observes ProcessInjectionPoint<?, T> e) {
        if (!this.enabled) {
            return;
        }
        Set<Annotation> qualifiers = e.getInjectionPoint().getQualifiers();
        if (qualifiers.contains(ContainerManaged.Literal.INSTANCE)) {
            if (qualifiers.contains(JtaTransactionScoped.Literal.INSTANCE) && qualifiers.contains(Extended.Literal.INSTANCE)
                || qualifiers.contains(Synchronized.Literal.INSTANCE) && qualifiers.contains(Unsynchronized.Literal.INSTANCE)) {
                e.addDefinitionError(new InjectionException("Invalid injection point; some qualifiers are mutually exclusive: "
                                                            + qualifiers));
            } else {
                this.containerManagedEntityManagerQualifiers.add(qualifiers);
            }
        }
    }

    /**
     * Adds various beans that integrate container-mode JPA into CDI SE.
     *
     * <p>This method first converts {@code META-INF/persistence.xml} resources into {@link PersistenceUnitInfo} objects
     * and takes into account any other {@link PersistenceUnitInfo} objects that already exist and ensures that all of
     * them are registered as CDI beans.</p>
     *
     * <p>This allows other CDI-provider-specific mechanisms to use these {@link PersistenceUnitInfo} beans as inputs
     * for creating {@link EntityManager} instances.</p>
     *
     * <p>Next, this method adds beans to produce {@link EntityManager}s and {@link EntityManagerFactory} instances in
     * accordance with the JPA specification.</p>
     *
     * @param event the {@link AfterBeanDiscovery} event describing the fact that bean discovery has been performed;
     * must not be {@code null}
     *
     * @param bm the {@link BeanManager} currently in effect; must not be {@code null}
     *
     * @see PersistenceUnitInfo
     *
     * @see #addPersistenceProviderBeansIfAbsent(AfterBeanDiscoveryEvent, BeanManager, Set, Iterable)
     *
     * @see #processPersistenceXmls(AfterBeanDiscoveryEvent, BeanManager, ClassLoader, Enumeration, Iterable, boolean)
     *
     * @see #processImplicitPersistenceUnits(AfterBeanDiscoveryEvent, Iterable)
     *
     * @see #addContainerManagedJpaBeans(AfterBeanDiscovery)
     */
    private void addSyntheticBeans(@Observes @Priority(LIBRARY_AFTER) AfterBeanDiscovery event, BeanManager bm) {
        if (!this.enabled) {
            return;
        }
        Iterable<? extends PersistenceProvider> providers = addPersistenceProviderBeans(event);

        // Should we consider type-level @PersistenceContext definitions of persistence units ("implicits")?
        boolean processImplicits = true;

        // Collect all pre-existing PersistenceUnitInfo beans (i.e. supplied by the end user) and make sure their
        // associated PersistenceProviders are beanified.  (Almost always this Set will be empty.)
        Set<Bean<?>> preexistingPuiBeans = bm.getBeans(PersistenceUnitInfo.class, Any.Literal.INSTANCE);
        if (!preexistingPuiBeans.isEmpty()) {
            processImplicits = false;
            this.addPersistenceProviderBeansIfAbsent(event, bm, preexistingPuiBeans, providers);
        }

        // Next, and most commonly, load all META-INF/persistence.xml resources with JAXB, and turn them into
        // PersistenceUnitInfo instances, and add beans for all of them as well as their associated PersistenceProviders
        // (if applicable).
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> urls;
        try {
            urls = classLoader.getResources("META-INF/persistence.xml");
        } catch (IOException e) {
            event.addDefinitionError(e);
            processImplicits = false;
            urls = Collections.emptyEnumeration();
        }
        if (urls.hasMoreElements()) {
            processImplicits = false;
            this.processPersistenceXmls(event,
                                        bm,
                                        classLoader,
                                        urls,
                                        providers,
                                        !preexistingPuiBeans.isEmpty());
        }

        // If we did not find any PersistenceUnitInfo instances via any other means, only then look at those defined
        // "implicitly", i.e. via type-level @PersistenceContext annotations.
        if (processImplicits) {
            this.processImplicitPersistenceUnits(event, providers);
        }

        // Add beans to support JPA.  In some cases, JTA must be present (see JPA section 7.5, for example: "A
        // container-managed entity manager must be a JTA entity manager.").
        this.addContainerManagedJpaBeans(event);

        // Clear out no-longer-needed-or-used collections to save memory.
        this.containerManagedEntityManagerFactoryQualifiers.clear();
        this.containerManagedEntityManagerQualifiers.clear();
        this.implicitPersistenceUnits.clear();
        this.unlistedManagedClassesByUnitNames.clear();
    }


    /*
     * Other instance methods.
     */


    /**
     * Reconfigures annotations on an {@linkplain #isEligiblePersistenceContextAnnotated(Annotated) eligible
     * <code>PersistenceContext</code>-annotated <code>Annotated</code>} such that the resulting {@link Annotated} is a
     * true CDI injection point representing all the same information.
     *
     * <p>The original {@link PersistenceContext} annotation is removed.</p>
     *
     * @param fc the {@link AnnotatedFieldConfigurator} that allows the field to be re-annotated; must not be {@code
     * null}
     *
     * @exception NullPointerException if {@code fc} is {@code null}
     */
    private <T> void rewritePersistenceContextFieldAnnotations(AnnotatedFieldConfigurator<T> fc) {
        this.rewrite(fc,
                     PersistenceContext.class,
                     EntityManager.class,
                     PersistenceContext::unitName,
                     (f, pc) -> {
                         f.add(pc.type() == EXTENDED
                               ? Extended.Literal.INSTANCE
                               : JtaTransactionScoped.Literal.INSTANCE)
                          .add(pc.synchronization() == UNSYNCHRONIZED
                               ? Unsynchronized.Literal.INSTANCE
                               : Synchronized.Literal.INSTANCE);
                         for (PersistenceProperty pp : pc.properties()) {
                             String ppName = pp.name();
                             if (!ppName.isBlank()) {
                                 f.add(pp);
                             }
                         }
                     });
    }

    /**
     * Reconfigures annotations on an {@linkplain #isEligiblePersistenceUnitAnnotated(Annotated) eligible
     * <code>PersistenceUnit</code>-annotated <code>Annotated</code>} such that the resulting {@link Annotated} is a
     * true CDI injection point representing all the same information.
     *
     * <p>The original {@link PersistenceUnit} annotation is removed.</p>
     *
     * @param fc the {@link AnnotatedFieldConfigurator} that allows the field to be re-annotated; must not be {@code
     * null}
     *
     * @exception NullPointerException if {@code fc} is {@code null}
     */
    private <T> void rewritePersistenceUnitFieldAnnotations(AnnotatedFieldConfigurator<T> fc) {
        this.rewrite(fc, PersistenceUnit.class, EntityManagerFactory.class, PersistenceUnit::unitName);
    }

    private <T> void rewritePersistenceContextInitializerMethodAnnotations(AnnotatedMethodConfigurator<T> mc) {
        this.rewrite(mc,
                     PersistenceContext.class,
                     EntityManager.class,
                     PersistenceContext::unitName,
                     (p, pc) -> {
                         p.add(pc.type() == EXTENDED
                               ? Extended.Literal.INSTANCE
                               : JtaTransactionScoped.Literal.INSTANCE)
                          .add(pc.synchronization() == UNSYNCHRONIZED
                               ? Unsynchronized.Literal.INSTANCE
                               : Synchronized.Literal.INSTANCE);
                         for (PersistenceProperty pp : pc.properties()) {
                             p.add(pp);
                         }
                     });
    }

    private <T> void rewritePersistenceUnitInitializerMethodAnnotations(AnnotatedMethodConfigurator<T> mc) {
        this.rewrite(mc, PersistenceUnit.class, EntityManagerFactory.class, PersistenceUnit::unitName);
    }

    private <T, A extends Annotation> void rewrite(AnnotatedFieldConfigurator<T> fc,
                                                   Class<A> ac,
                                                   Class<?> c,
                                                   Function<? super A, ? extends String> unitNameFunction) {
        this.rewrite(fc, ac, c, unitNameFunction, PersistenceExtension::sink);
    }

    private <T, A extends Annotation> void rewrite(AnnotatedFieldConfigurator<T> fc,
                                                   Class<A> ac,
                                                   Class<?> c,
                                                   Function<? super A, ? extends String> unitNameFunction,
                                                   BiConsumer<? super AnnotatedFieldConfigurator<T>, ? super A> adder) {
        Annotated f = fc.getAnnotated();
        if (!f.isAnnotationPresent(Inject.class) && f.getBaseType() instanceof Class<?> c2 && c.isAssignableFrom(c2)) {
            A a = fc.getAnnotated().getAnnotation(ac);
            if (a != null) {
                // Rewrite:
                //
                //   @PersistenceContext(properties = { @PersistenceProperty(name = "a", value = "b"),
                //                                      @PersistenceProperty(name = "c", value = "d") },
                //                       synchronization = SynchronizationType.SYNCHRONIZED,
                //                       type = PersistenceContextType.TRANSACTION,
                //                       unitName = "xyz")
                //   private EntityManager em;
                //
                //   @PersistenceUnit(unitName = "xyz")
                //   private EntityManagerFactory emf;
                //
                // ...to:
                //
                //   @Inject
                //   @ContainerManaged
                //   @JtaTransactionScoped
                //   @PersistenceProperty(name = "a", value = "b")
                //   @PersistenceProperty(name = "c", value = "d")
                //   @Named("xyz")
                //   @Synchronized
                //   private EntityManager em;
                //
                //   @Inject
                //   @ContainerManaged
                //   @Named("xyz")
                //   private EntityManagerFactory emf;
                //
                fc.add(InjectLiteral.INSTANCE);
                fc.add(ContainerManaged.Literal.INSTANCE);
                String unitName = unitNameFunction.apply(a);
                if (unitName == null || unitName.isBlank()) {
                    unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                }
                fc.add(NamedLiteral.of(unitName));
                adder.accept(fc, a);
                fc.remove(fa -> fa == a);
            }
        }
    }

    private <T, A extends Annotation> void rewrite(AnnotatedMethodConfigurator<T> mc,
                                                   Class<A> ac,
                                                   Class<?> c,
                                                   Function<? super A, ? extends String> unitNameFunction) {
        this.rewrite(mc, ac, c, unitNameFunction, PersistenceExtension::sink);
    }

    private <T, A extends Annotation> void rewrite(AnnotatedMethodConfigurator<T> mc,
                                                   Class<A> ac,
                                                   Class<?> c,
                                                   Function<? super A, ? extends String> unitNameFunction,
                                                   BiConsumer<? super AnnotatedParameterConfigurator<T>, ? super A> adder) {
        Annotated m = mc.getAnnotated();
        if (!m.isAnnotationPresent(Inject.class)) {
            A a = m.getAnnotation(ac);
            if (a != null) {
                boolean observerMethod = false;
                for (AnnotatedParameterConfigurator<T> apc : mc.params()) {
                    Annotated p = apc.getAnnotated();
                    if (p.isAnnotationPresent(Observes.class)) {
                        if (!observerMethod) {
                            observerMethod = true;
                        }
                    } else if (p.getBaseType() instanceof Class<?> pc && c.isAssignableFrom(pc)) {
                        // Rewrite:
                        //
                        //   @PersistenceContext(properties = { @PersistenceProperty(name = "a", value = "b"),
                        //                                      @PersistenceProperty(name = "c", value = "d") },
                        //                       synchronization = SynchronizationType.SYNCHRONIZED,
                        //                       type = PersistenceContextType.TRANSACTION,
                        //                       unitName = "xyz")
                        //   private void frob(EntityManager em) {}
                        //
                        //   @PersistenceUnit(unitName = "xyz")
                        //   private void frob(EntityManagerFactory emf) {}
                        //
                        // ...to:
                        //
                        //   @Inject
                        //   private void frob(@ContainerManaged
                        //                     @JtaTransactionScoped
                        //                     @Named("xyz")
                        //                     @PersistenceProperty(name = "a", value = "b")
                        //                     @PersistenceProperty(name = "c", value = "d")
                        //                     @Synchronized
                        //                     EntityManager em) {}
                        //
                        //   @Inject
                        //   private void frob(@ContainerManaged
                        //                     @Named("xyz")
                        //                     EntityManagerFactory emf) {}
                        //
                        apc.add(ContainerManaged.Literal.INSTANCE);
                        String unitName = unitNameFunction.apply(a);
                        if (unitName == null || unitName.isBlank()) {
                            unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                        }
                        apc.add(NamedLiteral.of(unitName));
                        adder.accept(apc, a);
                    }
                }
                mc.remove(ma -> ma == a);
                if (!observerMethod) {
                    mc.add(InjectLiteral.INSTANCE);
                }
            }
        }
    }

    /**
     * Given {@link Set}s of {@link PersistenceContext} and {@link PersistenceUnit} annotations that will be used for
     * their {@code unitName} elements only, associates the supplied {@link Class} with the persistence units implied by
     * the annotations.
     *
     * @param pcs a {@link Set} of {@link PersistenceContext}s whose {@link
     * PersistenceContext#unitName() unitName} elements identify persistence units; may be {@code null} or {@linkplain
     * Collection#isEmpty() empty}
     *
     * @param pus a {@link Set} of {@link PersistenceUnit}s whose {@link PersistenceUnit#unitName()
     * unitName} elements identify persistence units; may be {@code null} or {@linkplain Collection#isEmpty() empty}
     *
     * @param c the {@link Class} to associate; may be {@code null} in which case no action will be taken
     *
     * @exception NullPointerException if either {@code pcs}, {@code pus} or {@code c} is {@code null}
     *
     * @see PersistenceContext
     *
     * @see PersistenceUnit
     */
    private void assignManagedClassToPersistenceUnit(Set<? extends PersistenceContext> pcs,
                                                     Set<? extends PersistenceUnit> pus,
                                                     Class<?> c) {
        boolean processed = false;
        for (PersistenceContext pc : pcs) {
            if (!processed) {
                processed = true;
            }
            String unitName = pc.unitName();
            if (unitName.isBlank()) {
                unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
            }
            addUnlistedManagedClass(unitName, c);
        }
        for (PersistenceUnit pu : pus) {
            if (!processed) {
                processed = true;
            }
            String unitName = pu.unitName();
            if (unitName.isBlank()) {
                unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
            }
            addUnlistedManagedClass(unitName, c);
        }
        if (!processed) {
            addUnlistedManagedClass(DEFAULT_PERSISTENCE_UNIT_NAME, c);
        }
    }

    /**
     * Given a {@link Class} and a name of a persistence unit, associates the {@link Class} with that persistence unit
     * as a member of its list of managed classes.
     *
     * @param unitName the name of the persistence unit in question; may be {@code null}
     *
     * @param mc the {@link Class} to associate; may be {@code null} in which case no action will be taken
     *
     * @exception NullPointerException if either {@code unitName} or {@code mc} is {@code null}
     *
     * @see PersistenceUnitInfo#getManagedClassNames()
     */
    private void addUnlistedManagedClass(String unitName, Class<?> mc) {
        this.unlistedManagedClassesByUnitNames.computeIfAbsent(unitName.isBlank() ? DEFAULT_PERSISTENCE_UNIT_NAME : unitName,
                                                               k -> new HashSet<>())
            .add(mc);
    }

    private static Iterable<? extends PersistenceProvider> addPersistenceProviderBeans(AfterBeanDiscovery e) {
        PersistenceProviderResolver resolver = PersistenceProviderResolverHolder.getPersistenceProviderResolver();

        // Provide support for, e.g.:
        //   @Inject
        //   private PersistenceProviderResolver ppr;
        e.addBean()
            .addTransitiveTypeClosure(PersistenceProviderResolver.class)
            .scope(Singleton.class)
            .createWith(cc -> resolver);
        Collection<? extends PersistenceProvider> providers = resolver.getPersistenceProviders();
        for (PersistenceProvider provider : providers) {
            // Provide support for, e.g.:
            //   @Inject
            //   private MyPersistenceProviderSubclassMaybeFromPersistenceXml ppr;
            e.addBean()
                .addTransitiveTypeClosure(provider.getClass())
                .scope(Singleton.class)
                .createWith(cc -> provider);
        }
        return providers;
    }

    private void addPersistenceProviderBeansIfAbsent(AfterBeanDiscovery event,
                                                     BeanManager bm,
                                                     Set<Bean<?>> preexistingPuiBeans,
                                                     Iterable<? extends PersistenceProvider> providers) {
        for (Object bean : preexistingPuiBeans) {
            @SuppressWarnings("unchecked")
            Bean<PersistenceUnitInfo> preexistingPuiBean = (Bean<PersistenceUnitInfo>) bean;
            // We use Contextual#create() directly to create a PersistenceUnitInfo contextual instance (normally for
            // this use case in CDI you would acquire a contextual reference via BeanManager#getReference(), but it is
            // too early in the (spec-defined) lifecycle to do that here).  We also deliberately do not use
            // Context#get(Contextual, CreationalContext), since that might "install" the instance so acquired in
            // whatever Context/scope it is defined in and we just need it transiently.
            //
            // Getting a contextual instance this way, via Contextual#create(), is normally frowned upon, since it
            // bypasses CDI's Context mechansims and proxying and interception features (it is the foundation upon which
            // they are built), but here we need the instance only for the return values of
            // getPersistenceProviderClassName() and getClassLoader().  We then destroy the instance immediately so that
            // everything behaves as though this contextual instance acquired by shady means never existed.
            CreationalContext<PersistenceUnitInfo> cc = bm.createCreationalContext(null);
            try {
                PersistenceUnitInfo pui = preexistingPuiBean.create(cc);
                try {
                    this.addPersistenceProviderBeanIfAbsent(event, pui, providers);
                } finally {
                    preexistingPuiBean.destroy(pui, cc);
                }
            } finally {
                cc.release();
            }
        }
    }

    /**
     * Given a {@link PersistenceUnitInfo} and a {@link Collection} of {@link PersistenceProvider} instances
     * representing already "beanified" {@link PersistenceProvider}s, adds a CDI bean for the {@linkplain
     * PersistenceUnitInfo#getPersistenceProviderClassName() persistence provider referenced by the supplied
     * <code>PersistenceUnitInfo</code>} if the supplied {@link Collection} of {@link PersistenceProvider}s does not
     * contain an instance of it.
     *
     * @param event the {@link AfterBeanDiscovery} event that will do the actual bean addition; must not be {@code null}
     *
     * @param pui the {@link PersistenceUnitInfo} whose {@linkplain
     * PersistenceUnitInfo#getPersistenceProviderClassName() associated persistence provider} will be beanified; must
     * not be {@code null}
     *
     * @param providers an {@link Iterable} of {@link PersistenceProvider} instances that represent {@link
     * PersistenceProvider}s that have already had beans added for them; may be {@code null}
     *
     * @exception NullPointerException if {@code event} or {@code pui} is {@code null}
     *
     * @exception ReflectiveOperationException if an error occurs during reflection
     */
    private void addPersistenceProviderBeanIfAbsent(AfterBeanDiscovery event,
                                                    PersistenceUnitInfo pui,
                                                    Iterable<? extends PersistenceProvider> providers) {
        String providerClassName = pui.getPersistenceProviderClassName();
        if (providerClassName == null) {
            return;
        }

        for (final PersistenceProvider provider : providers) {
            if (provider.getClass().getName().equals(providerClassName)) {
                return;
            }
        }

        // The PersistenceProvider class in question is not one we already loaded.  Add a bean for it too.
        String unitName = pui.getPersistenceUnitName();
        if (unitName.isBlank()) {
            unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
        }

        // Provide support for, e.g.:
        //   @Inject
        //   @Named("test")
        //   private PersistenceProvider providerProbablyReferencedFromAPersistenceXml;
        event.addBean()
            .addTransitiveTypeClosure(PersistenceProvider.class)
            .scope(Singleton.class)
            .qualifiers(NamedLiteral.of(unitName))
            .createWith(cc -> {
                    try {
                        ClassLoader classLoader = pui.getClassLoader();
                        if (classLoader == null) {
                            classLoader = Thread.currentThread().getContextClassLoader();
                        }
                        return Class.forName(providerClassName, true, classLoader).getDeclaredConstructor().newInstance();
                    } catch (final ReflectiveOperationException e) {
                        throw new CreationException(e.getMessage(), e);
                    }
                });
    }

    private void processPersistenceXmls(AfterBeanDiscovery event,
                                        BeanManager bm,
                                        ClassLoader classLoader,
                                        Enumeration<URL> persistenceXmlUrls,
                                        Iterable<? extends PersistenceProvider> providers,
                                        boolean userSuppliedPuiBeans) {
        if (!persistenceXmlUrls.hasMoreElements()) {
            return;
        }
        // We use StAX for XML loading because it is the same XML parsing strategy used by all known CDI
        // implementations.  If the end user wants to customize the StAX implementation then we want that customization
        // to apply here as well.
        XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
        // See
        // https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.md#xmlinputfactory-a-stax-parser
        xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xmlInputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        Unmarshaller unmarshaller;
        try {
            unmarshaller = JAXBContext.newInstance(Persistence.class.getPackage().getName()).createUnmarshaller();
        } catch (JAXBException e) {
            event.addDefinitionError(e);
            return;
        }
        Supplier<? extends DataSourceProvider> dataSourceProviderSupplier =
            () -> bm.createInstance().select(DataSourceProvider.class).get();
        PersistenceUnitInfoBean solePui = null;
        Supplier<? extends ClassLoader> tempClassLoaderSupplier =
            classLoader instanceof URLClassLoader ucl ? () -> new URLClassLoader(ucl.getURLs()) : () -> classLoader;
        for (int puCount = 0; persistenceXmlUrls.hasMoreElements();) {
            URL persistenceXmlUrl = persistenceXmlUrls.nextElement();
            Persistence persistence = null;
            try (InputStream inputStream = new BufferedInputStream(persistenceXmlUrl.openStream())) {
                XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(inputStream);
                try {
                    persistence = (Persistence) unmarshaller.unmarshal(reader);
                } finally {
                    reader.close();
                }
            } catch (IOException | JAXBException | XMLStreamException e) {
                event.addDefinitionError(e);
                continue;
            }
            URL persistenceRootUrl;
            try {
                URI persistenceXmlUri = persistenceXmlUrl.toURI();
                if ("jar".equalsIgnoreCase(persistenceXmlUri.getScheme())) {
                    String ssp = persistenceXmlUri.getSchemeSpecificPart();
                    persistenceRootUrl = new URI(ssp.substring(0, ssp.lastIndexOf('!'))).toURL();
                } else {
                    persistenceRootUrl = persistenceXmlUri.resolve("..").toURL();
                }
            } catch (MalformedURLException | URISyntaxException e) {
                event.addDefinitionError(e);
                continue;
            }
            for (Persistence.PersistenceUnit pu : persistence.getPersistenceUnit()) {
                PersistenceUnitInfoBean pui;
                try {
                    pui = PersistenceUnitInfoBean.fromPersistenceUnit(pu,
                                                                      classLoader,
                                                                      tempClassLoaderSupplier,
                                                                      persistenceRootUrl,
                                                                      unlistedManagedClassesByUnitNames,
                                                                      dataSourceProviderSupplier);
                } catch (MalformedURLException e) {
                    event.addDefinitionError(e);
                    continue;
                }
                String unitName = pui.getPersistenceUnitName();
                if (unitName == null || unitName.isBlank()) {
                    unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
                }
                Named qualifier = NamedLiteral.of(unitName);
                // Provide support for, e.g.:
                //   @Inject
                //   @Named("test")
                //   private PersistenceUnitInfo persistenceUnitInfo;
                event.addBean()
                    .beanClass(PersistenceUnitInfoBean.class)
                    .addTransitiveTypeClosure(PersistenceUnitInfoBean.class)
                    .scope(Singleton.class)
                    .qualifiers(qualifier)
                    .produceWith(i -> producePersistenceUnitInfoBean(i, pui, qualifier));
                addPersistenceProviderBeanIfAbsent(event, pui, providers);
                if (puCount == 0) {
                    solePui = pui;
                } else if (solePui != null) {
                    solePui = null;
                }
                puCount++;
            }
        }
        if (!userSuppliedPuiBeans && solePui != null) {
            String soleUnitName = solePui.getPersistenceUnitName();
            assert soleUnitName != null;
            assert !soleUnitName.isBlank();
            if (!soleUnitName.equals(DEFAULT_PERSISTENCE_UNIT_NAME)) {
                PersistenceUnitInfoBean pui = solePui;
                // Provide support for, e.g.:
                //   @Inject
                //   @Named("__DEFAULT__"))
                //   private PersistenceUnitInfo persistenceUnitInfo;
                Named qualifier = NamedLiteral.of(DEFAULT_PERSISTENCE_UNIT_NAME);
                event.addBean()
                    .beanClass(PersistenceUnitInfoBean.class)
                    .addTransitiveTypeClosure(PersistenceUnitInfoBean.class)
                    .scope(Singleton.class)
                    .qualifiers(qualifier)
                    .produceWith(i -> producePersistenceUnitInfoBean(i, pui, qualifier));
            }
        }
    }

    private void processImplicitPersistenceUnits(AfterBeanDiscovery event, Iterable<? extends PersistenceProvider> providers) {
        PersistenceUnitInfoBean solePui = null;
        int puCount = 0;
        for (PersistenceUnitInfoBean pui : this.implicitPersistenceUnits.values()) {
            String unitName = pui.getPersistenceUnitName();
            if (unitName == null || unitName.isBlank()) {
                unitName = DEFAULT_PERSISTENCE_UNIT_NAME;
            }
            if (!pui.excludeUnlistedClasses()) {
                Collection<? extends Class<?>> unlistedManagedClasses = this.unlistedManagedClassesByUnitNames.get(unitName);
                if (unlistedManagedClasses != null) {
                    for (Class<?> unlistedManagedClass : unlistedManagedClasses) {
                        pui.addManagedClassName(unlistedManagedClass.getName());
                    }
                }
            }
            Named qualifier = NamedLiteral.of(unitName);
            // Provide support for, e.g.:
            //   @Inject
            //   @Named("test")
            //   private PersistenceUnitInfo persistenceUnitInfo;
            event.addBean()
                .beanClass(PersistenceUnitInfoBean.class)
                .addTransitiveTypeClosure(PersistenceUnitInfoBean.class)
                .scope(Singleton.class)
                .qualifiers(qualifier)
                .produceWith(i -> producePersistenceUnitInfoBean(i, pui, qualifier));
            addPersistenceProviderBeanIfAbsent(event, pui, providers);
            if (puCount == 0) {
                solePui = pui;
            } else if (solePui != null) {
                solePui = null;
            }
            puCount++;
        }
        if (solePui != null) {
            // Add a bean for the DEFAULT_PERSISTENCE_UNIT_NAME qualifier too.
            String soleUnitName = solePui.getPersistenceUnitName();
            assert soleUnitName != null;
            assert !soleUnitName.isBlank();
            if (!soleUnitName.equals(DEFAULT_PERSISTENCE_UNIT_NAME)) {
                PersistenceUnitInfoBean pui = solePui;
                Named qualifier = NamedLiteral.of(DEFAULT_PERSISTENCE_UNIT_NAME);
                // Provide support for, e.g.:
                //   @Inject
                //   @Named("__DEFAULT__")
                //   private PersistenceUnitInfo persistenceUnitInfo;
                event.addBean()
                    .beanClass(PersistenceUnitInfoBean.class)
                    .addTransitiveTypeClosure(PersistenceUnitInfoBean.class)
                    .scope(Singleton.class)
                    .qualifiers(qualifier)
                    .produceWith(i -> producePersistenceUnitInfoBean(i, pui, qualifier));
            }
        }
    }

    /**
     * Adds certain beans to support injection of {@link EntityManagerFactory} and {@link EntityManager} instances
     * according to the JPA specification.
     *
     * @param event an {@link AfterBeanDiscovery} container lifecycle event; must not be {@code null}
     *
     * @exception NullPointerException if {@code event} is {@code null}
     *
     * @see #addContainerManagedEntityManagerFactoryBeans(AfterBeanDiscovery, Set)
     *
     * @see #addExtendedEntityManagerBeans(AfterBeanDiscovery, Set)
     *
     * @see #addJtaTransactionScopedEntityManagerBeans(AfterBeanDiscovery, Set)
     */
    private void addContainerManagedJpaBeans(AfterBeanDiscovery event) {
        for (Iterator<Set<Annotation>> i = this.entityManagerFactoryQualifiers.iterator(); i.hasNext();) {
            Set<Annotation> qualifiers = i.next();
            i.remove();
            if (qualifiers.contains(ContainerManaged.Literal.INSTANCE)) {
                addContainerManagedEntityManagerFactoryBeans(event, qualifiers);
            }
        }
        assert this.entityManagerFactoryQualifiers.isEmpty();
        if (this.transactionsSupported) {
            for (Set<Annotation> qualifiers : this.containerManagedEntityManagerQualifiers) {
                if (qualifiers.contains(ContainerManaged.Literal.INSTANCE)) {
                    // Note that each add* method invoked below is responsible for ensuring that it adds beans only once
                    // if at all, i.e. for validating and de-duplicating the qualifiers that it is supplied with if
                    // necessary.
                    addContainerManagedEntityManagerFactoryBeans(event, qualifiers);
                    if (qualifiers.contains(Extended.Literal.INSTANCE)) {
                        assert !qualifiers.contains(JtaTransactionScoped.Literal.INSTANCE);
                        addExtendedEntityManagerBeans(event, qualifiers);
                    } else {
                        assert qualifiers.contains(JtaTransactionScoped.Literal.INSTANCE);
                        addJtaTransactionScopedEntityManagerBeans(event, qualifiers);
                    }
                }
            }
        } else {
            for (Set<Annotation> qualifiers : this.containerManagedEntityManagerQualifiers) {
                if (qualifiers.contains(ContainerManaged.Literal.INSTANCE)) {
                    // Note that each add* method invoked below is responsible for ensuring that it adds beans only once
                    // if at all, i.e. for validating the qualifiers that it is supplied with.
                    addContainerManagedEntityManagerFactoryBeans(event, qualifiers);
                }
            }
        }
    }

    private void addContainerManagedEntityManagerFactoryBeans(AfterBeanDiscovery event,
                                                              Set<? extends Annotation> suppliedQualifiers) {
        // Provide support for, e.g.:
        //   @Inject
        //   @ContainerManaged
        //   @Named("test")
        //   private final EntityManagerFactory emf;
        Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.removeIf(e -> e != ContainerManaged.Literal.INSTANCE && SyntheticJpaQualifiers.INSTANCE.contains(e));
        if (this.containerManagedEntityManagerFactoryQualifiers.add(qualifiers)) {
            event.addBean()
                .addTransitiveTypeClosure(EntityManagerFactory.class)
                .scope(Singleton.class)
                .qualifiers(qualifiers)
                .produceWith(PersistenceExtension::produceEntityManagerFactory)
                .disposeWith(PersistenceExtension::disposeEntityManagerFactory);
        }
    }

    private void addExtendedEntityManagerBeans(AfterBeanDiscovery event, Set<Annotation> suppliedQualifiers) {
        if (!this.transactionsSupported) {
            event.addDefinitionError(new IllegalStateException("Transactions are not supported"));
            return;
        }
        // Provide support for, e.g.:
        //   @Inject
        //   @ContainerManaged
        //   @Extended
        //   @PersistenceProperty(name = "a", value = "b")
        //   @PersistenceProperty(name = "c", value = "d")
        //   @Synchronized // or @Unsynchronized, or none
        //   @Named("test")
        //   private final EntityManager extendedEm;
        Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.removeIf(e -> e == JtaTransactionScoped.Literal.INSTANCE);
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        qualifiers.add(Extended.Literal.INSTANCE);
        event.addBean()
            .addTransitiveTypeClosure(JtaExtendedEntityManager.class)
            .scope(Dependent.class) // critical: must be Dependent scope
            .qualifiers(qualifiers)
            .produceWith(PersistenceExtension::produceJtaExtendedEntityManager)
            .disposeWith(PersistenceExtension::disposeJtaExtendedEntityManager);
    }

    private void addJtaTransactionScopedEntityManagerBeans(AfterBeanDiscovery event, Set<Annotation> suppliedQualifiers) {
        if (!this.transactionsSupported) {
            event.addDefinitionError(new IllegalStateException("Transactions are not supported"));
            return;
        }
        // Provide support for, e.g.:
        //   @Inject
        //   @ContainerManaged
        //   @JtaTransactionScoped
        //   @PersistenceProperty(name = "a", value = "b")
        //   @PersistenceProperty(name = "c", value = "d")
        //   @Synchronized // or @Unsynchronized, or none
        //   @Named("test")
        //   private final EntityManager jtaTransactionScopedEm;
        Set<Annotation> qualifiers = new HashSet<>(suppliedQualifiers);
        qualifiers.removeIf(e -> e == Extended.Literal.INSTANCE);
        qualifiers.add(ContainerManaged.Literal.INSTANCE);
        qualifiers.add(JtaTransactionScoped.Literal.INSTANCE);
        event.addBean()
            .addTransitiveTypeClosure(JtaEntityManager.class)
            .scope(Dependent.class) // critical: must be Dependent scope
            .qualifiers(qualifiers)
            .produceWith(PersistenceExtension::produceJtaEntityManager)
            .disposeWith(PersistenceExtension::disposeJtaEntityManager);
    }


    /*
     * Static methods.
     */


    private static JtaExtendedEntityManager produceJtaExtendedEntityManager(Instance<Object> instance) {
        BeanAttributes<JtaExtendedEntityManager> ba = instance.select(BEAN_JTAEXTENDEDENTITYMANAGER_TYPELITERAL).get();
        Set<Annotation> containerManagedSelectionQualifiers = new HashSet<>();
        containerManagedSelectionQualifiers.add(ContainerManaged.Literal.INSTANCE);
        Set<Annotation> selectionQualifiers = new HashSet<>();
        SynchronizationType syncType = null;
        Map<String, String> properties = new HashMap<>();
        for (Annotation beanQualifier : ba.getQualifiers()) {
            if (beanQualifier == Any.Literal.INSTANCE) {
                continue;
            } else if (beanQualifier == Unsynchronized.Literal.INSTANCE) {
                if (syncType == null) {
                    syncType = UNSYNCHRONIZED;
                }
            } else if (beanQualifier instanceof PersistenceProperty pp) {
                containerManagedSelectionQualifiers.add(pp);
                String ppName = pp.name();
                if (!ppName.isBlank()) {
                    properties.put(ppName, pp.value());
                }
            } else if (beanQualifier != Any.Literal.INSTANCE && !SyntheticJpaQualifiers.INSTANCE.contains(beanQualifier)) {
                containerManagedSelectionQualifiers.add(beanQualifier);
                selectionQualifiers.add(beanQualifier);
            }
        }
        SynchronizationType finalSyncType = syncType == null ? SYNCHRONIZED : syncType;
        return instance.select(ReferenceCountingProducer.class)
            .get()
            .produce(() -> {
                    TransactionRegistry tr =
                        getOrDefault(instance.select(TransactionRegistry.class),
                                     selectionQualifiers.toArray(EMPTY_ANNOTATION_ARRAY));
                    return
                        new JtaExtendedEntityManager(tr::active,
                                                     tr::get,
                                                     tr::put,
                                                     instance.select(EntityManagerFactory.class,
                                                                     containerManagedSelectionQualifiers
                                                                     .toArray(EMPTY_ANNOTATION_ARRAY))
                                                     .get()
                                                     .createEntityManager(finalSyncType, properties),
                                                     finalSyncType == SYNCHRONIZED);
                },
                JtaExtendedEntityManager::dispose,
                JtaExtendedEntityManager.class,
                containerManagedSelectionQualifiers);
    }

    private static void disposeJtaExtendedEntityManager(JtaExtendedEntityManager em, Instance<Object> instance) {
        Set<Annotation> containerManagedSelectionQualifiers = new HashSet<>();
        for (Annotation beanQualifier : instance.select(BEAN_JTAEXTENDEDENTITYMANAGER_TYPELITERAL).get().getQualifiers()) {
            if (beanQualifier == ContainerManaged.Literal.INSTANCE
                || beanQualifier != Any.Literal.INSTANCE
                && !SyntheticJpaQualifiers.INSTANCE.contains(beanQualifier)) {
                containerManagedSelectionQualifiers.add(beanQualifier);
            }
        }
        instance.select(ReferenceCountingProducer.class)
            .get()
            .dispose(JtaExtendedEntityManager.class,
                     containerManagedSelectionQualifiers);
    }

    private static PersistenceUnitInfoBean producePersistenceUnitInfoBean(Instance<Object> instance,
                                                                          PersistenceUnitInfoBean pui,
                                                                          Annotation... qualifiers) {
        // Permit arbitrary customization of the PersistenceUnitInfoBean right before it is produced.
        instance.select(EVENT_PERSISTENCEUNITINFOBEAN_TYPELITERAL, qualifiers).get().fire(pui);
        return pui;
    }

    private static EntityManagerFactory produceEntityManagerFactory(Instance<Object> instance) {
        BeanAttributes<EntityManagerFactory> ba = instance.select(BEAN_ENTITYMANAGERFACTORY_TYPELITERAL).get();
        Set<Annotation> selectionQualifiers = new HashSet<>();
        Set<Annotation> namedSelectionQualifiers = new HashSet<>();
        for (Annotation beanQualifier: ba.getQualifiers()) {
            if (beanQualifier == Any.Literal.INSTANCE) {
                continue;
            } else if (beanQualifier instanceof Named) {
                namedSelectionQualifiers.add(beanQualifier);
            } else if (!SyntheticJpaQualifiers.INSTANCE.contains(beanQualifier)) {
                namedSelectionQualifiers.add(beanQualifier);
                selectionQualifiers.add(beanQualifier);
            }
        }
        PersistenceUnitInfo pui =
            getOrDefault(instance.select(PersistenceUnitInfo.class), namedSelectionQualifiers);
        PersistenceProvider pp =
            getOrDefault(instance.select(PersistenceProvider.class), selectionQualifiers);
        Map<String, Object> properties = new HashMap<>(5);
        properties.put("jakarta.persistence.bean.manager", instance.select(BeanManager.class).get());
        try {
            Instance<?> vf =
                instance.select(Class.forName("jakarta.validation.ValidatorFactory",
                                              false,
                                              Thread.currentThread().getContextClassLoader()));
            if (!vf.isUnsatisfied()) {
                properties.put("jakarta.persistence.validation.factory", vf.get());
            }
        } catch (final ClassNotFoundException classNotFoundException) {

        }
        return pp.createContainerEntityManagerFactory(pui, properties);
    }

    private static void disposeEntityManagerFactory(EntityManagerFactory emf, Instance<Object> instance) {
        emf.close();
    }

    private static JtaEntityManager produceJtaEntityManager(Instance<Object> instance) {
        Set<Annotation> containerManagedSelectionQualifiers = new HashSet<>();
        containerManagedSelectionQualifiers.add(ContainerManaged.Literal.INSTANCE);
        Set<Annotation> selectionQualifiers = new HashSet<>();
        SynchronizationType syncType = null;
        Map<String, String> properties = new HashMap<>();
        for (Annotation beanQualifier : instance.select(BEAN_JTAENTITYMANAGER_TYPELITERAL).get().getQualifiers()) {
            if (beanQualifier == Any.Literal.INSTANCE) {
                continue;
            } else if (beanQualifier == Unsynchronized.Literal.INSTANCE) {
                if (syncType == null) {
                    syncType = UNSYNCHRONIZED;
                }
            } else if (beanQualifier instanceof PersistenceProperty pp) {
                String ppName = pp.name();
                if (!ppName.isBlank()) {
                    containerManagedSelectionQualifiers.add(pp);
                    properties.put(ppName, pp.value());
                }
            } else if (!SyntheticJpaQualifiers.INSTANCE.contains(beanQualifier)) {
                containerManagedSelectionQualifiers.add(beanQualifier);
                selectionQualifiers.add(beanQualifier);
            }
        }
        SynchronizationType finalSyncType = syncType == null ? SYNCHRONIZED : syncType;
        return instance.select(ReferenceCountingProducer.class)
            .get()
            .produce(() -> {
                    TransactionRegistry tr = getOrDefault(instance.select(TransactionRegistry.class), selectionQualifiers);
                    return
                        new JtaEntityManager(tr::active,
                                             tr::addCompletionListener,
                                             tr::get,
                                             tr::put,
                                             instance.select(EntityManagerFactory.class,
                                                             containerManagedSelectionQualifiers.toArray(EMPTY_ANNOTATION_ARRAY))
                                             .get()::createEntityManager,
                                             finalSyncType,
                                             properties);
                },
                JtaEntityManager::dispose,
                JtaEntityManager.class,
                containerManagedSelectionQualifiers);
    }

    private static void disposeJtaEntityManager(JtaEntityManager em, Instance<Object> instance) {
        Set<Annotation> containerManagedSelectionQualifiers = new HashSet<>();
        for (Annotation beanQualifier : instance.select(BEAN_JTAENTITYMANAGER_TYPELITERAL).get().getQualifiers()) {
            if (beanQualifier == ContainerManaged.Literal.INSTANCE
                || beanQualifier != Any.Literal.INSTANCE
                && !SyntheticJpaQualifiers.INSTANCE.contains(beanQualifier)) {
                containerManagedSelectionQualifiers.add(beanQualifier);
            }
        }
        instance.select(ReferenceCountingProducer.class)
            .get()
            .dispose(JtaEntityManager.class,
                     containerManagedSelectionQualifiers);
    }

    /**
     * Returns {@code true} if the supplied {@link Annotated} is annotated with {@link PersistenceContext}, is not
     * annotated with {@link Inject} and has a type assignable to {@link EntityManager}.
     *
     * @param a the {@link Annotated} in question; must not be {@code null}
     *
     * @return {@code true} if the supplied {@link Annotated} is annotated with {@link PersistenceContext}, is not
     * annotated with {@link Inject} and has a type assignable to {@link EntityManager}; {@code false} in all other
     * cases
     *
     * @exception NullPointerException if {@code a} is {@code null}
     */
    private static boolean isEligiblePersistenceContextAnnotated(Annotated a) {
        return isRewriteEligible(a, PersistenceContext.class, EntityManager.class);
    }

    /**
     * Returns {@code true} if the supplied {@link Annotated} is annotated with {@link PersistenceUnit}, is not
     * annotated with {@link Inject} and has a type assignable to {@link EntityManagerFactory}.
     *
     * @param a the {@link Annotated} in question; must not be {@code null}
     *
     * @return {@code true} if the supplied {@link Annotated} is annotated with {@link PersistenceUnit}, is not
     * annotated with {@link Inject} and has a type assignable to {@link EntityManagerFactory}; {@code false} in all
     * other cases
     *
     * @exception NullPointerException if {@code a} is {@code null}
     */
    private static boolean isEligiblePersistenceUnitAnnotated(Annotated a) {
        return isRewriteEligible(a, PersistenceUnit.class, EntityManagerFactory.class);
    }

    private static <A extends Annotation> boolean isRewriteEligible(Annotated a, Class<? extends A> ac, Class<?> c) {
        if (a instanceof AnnotatedCallable<?> am) {
            return isRewriteEligible(am, ac, c);
        }
        return
            a.isAnnotationPresent(ac) && !isInjectionPoint(a) && a.getBaseType() instanceof Class<?> bt && c.isAssignableFrom(bt);
    }

    private static <A extends Annotation> boolean isRewriteEligible(AnnotatedCallable<?> a, Class<? extends A> ac, Class<?> c) {
        if (a.isAnnotationPresent(ac) && !isInjectionPoint(a)) {
            for (Annotated ap : a.getParameters()) {
                if (ap.getBaseType() instanceof Class<?> bt && c.isAssignableFrom(bt)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isInjectionPoint(Annotated a) {
        return a.isAnnotationPresent(Inject.class);
    }

    private static URL locationOf(AnnotatedType<?> a) {
        return locationOf(a.getJavaClass());
    }

    private static URL locationOf(Class<?> c) {
        return locationOf(c.getProtectionDomain());
    }

    private static URL locationOf(ProtectionDomain pd) {
        return pd == null ? null : locationOf(pd.getCodeSource());
    }

    private static URL locationOf(CodeSource cs) {
        return cs == null ? null : cs.getLocation();
    }

    private static <T> T getOrDefault(Instance<T> i, Set<Annotation> q) {
        return getOrDefault(i, q.toArray(EMPTY_ANNOTATION_ARRAY));
    }

    private static <T> T getOrDefault(Instance<T> i, Annotation... q) {
        if (q != null && q.length > 0) {
            Instance<T> i2 = i.select(q);
            if (!i2.isUnsatisfied()) {
                return i2.get();
            }
        }
        return i.get();
    }

    private static void sink(Object o1, Object o2) {

    }

    private static final class SyntheticJpaQualifiers {

        private static final Set<Annotation> INSTANCE;

        static {
            Set<Annotation> q = new HashSet<>();
            q.add(ContainerManaged.Literal.INSTANCE);
            q.add(Extended.Literal.INSTANCE);
            q.add(JtaTransactionScoped.Literal.INSTANCE);
            q.add(Synchronized.Literal.INSTANCE);
            q.add(Unsynchronized.Literal.INSTANCE);
            INSTANCE = Set.copyOf(q);
        }

    }

    @Singleton
    private static final class ReferenceCountingProducer {

        private static final Logger LOGGER = Logger.getLogger(ReferenceCountingProducer.class.getName());

        private static final ThreadLocal<Map<ReferenceCountingProducer, Map<ProductionId, Production<?>>>> TL =
            ThreadLocal.withInitial(HashMap::new);

        @Inject
        private ReferenceCountingProducer() {
            super();
        }

        private <T> T produce(Supplier<? extends T> supplier,
                              Consumer<? super T> disposer,
                              Class<T> type,
                              Set<Annotation> qualifiers) {
            return this.produce(supplier, disposer, Set.of(type), qualifiers);
        }

        private <T> T produce(Supplier<? extends T> supplier,
                              Consumer<? super T> disposer,
                              Class<T> type,
                              Annotation... qualifiers) {
            return this.produce(supplier, disposer, Set.of(type), Set.copyOf(Arrays.asList(qualifiers)));
        }

        private <T> T produce(Supplier<? extends T> supplier,
                              Consumer<? super T> disposer,
                              TypeLiteral<T> type,
                              Set<Annotation> qualifiers) {
            return this.produce(supplier, disposer, Set.of(type.getType()), qualifiers);
        }

        private <T> T produce(Supplier<? extends T> supplier,
                              Consumer<? super T> disposer,
                              TypeLiteral<T> type,
                              Annotation... qualifiers) {
            return this.produce(supplier, disposer, Set.of(type.getType()), Set.copyOf(Arrays.asList(qualifiers)));
        }

        private <T> T produce(Supplier<? extends T> supplier,
                              Consumer<? super T> disposer,
                              Set<Type> types,
                              Annotation... qualifiers) {
            return this.produce(supplier, disposer, types, Set.copyOf(Arrays.asList(qualifiers)));
        }

        private <T> T produce(Supplier<? extends T> supplier,
                              Consumer<? super T> disposer,
                              Set<Type> types,
                              Set<Annotation> qualifiers) {
            Objects.requireNonNull(supplier, "supplier");
            Objects.requireNonNull(disposer, "disposer");
            @SuppressWarnings("unchecked")
            Production<T> production = (Production<T>) TL.get()
                .computeIfAbsent(this, k -> new HashMap<>())
                .computeIfAbsent(new ProductionId(Set.copyOf(types), Set.copyOf(qualifiers)),
                                 k -> new Production<>(supplier.get(), disposer));
            int rc = production.reference();
            assert rc > 0;
            return production.object;
        }

        private <T> void dispose(Class<T> type, Set<Annotation> qualifiers) {
            this.dispose(Set.of(type), qualifiers);
        }

        private <T> void dispose(Class<T> type, Annotation... qualifiers) {
            this.dispose(Set.of(type), Set.copyOf(Arrays.asList(qualifiers)));
        }

        private <T> void dispose(TypeLiteral<T> type, Set<Annotation> qualifiers) {
            this.dispose(Set.of(type.getType()), qualifiers);
        }

        private <T> void dispose(TypeLiteral<T> type, Annotation... qualifiers) {
            this.dispose(Set.of(type.getType()), Set.copyOf(Arrays.asList(qualifiers)));
        }

        private <T> void dispose(Set<Type> types, Annotation... qualifiers) {
            this.dispose(types, Set.copyOf(Arrays.asList(qualifiers)));
        }

        private <T> void dispose(Set<Type> types, Set<Annotation> qualifiers) {
            Map<ReferenceCountingProducer, Map<ProductionId, Production<?>>> tlMap = TL.get();
            Map<ProductionId, Production<?>> map = tlMap.get(this);
            if (map != null) {
                ProductionId id = new ProductionId(Set.copyOf(types), Set.copyOf(qualifiers));
                @SuppressWarnings("unchecked")
                Production<T> production = (Production<T>) map.get(id);
                if (production != null) {
                    int rc = production.unreference();
                    if (rc == 0) {
                        map.remove(id);
                        if (map.isEmpty()) {
                            tlMap.remove(this);
                        }
                    } else {
                        assert rc > 0;
                    }
                }
            }
        }

        @PreDestroy
        private void preDestroy() {
            Map<ProductionId, Production<?>> map = TL.get().remove(this);
            if (map != null) {
                // This map shouldn't exist. If it does exist, it should at least be empty. We shouldn't be here. Ensure
                // that everything is disposed properly nonetheless and log the problem.
                RuntimeException re = null;
                for (Entry<?, ? extends Production<?>> entry : map.entrySet()) {
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.logp(Level.WARNING, this.getClass().getName(), "preDestroy",
                                    "Disposing {0} but it should have already been disposed!", entry);
                    }
                    try {
                        entry.getValue().dispose();
                    } catch (RuntimeException e) {
                        if (re == null) {
                            re = e;
                        } else {
                            re.addSuppressed(e);
                        }
                    }
                }
                if (re != null) {
                    throw re;
                }
            }
        }

        private static final class Production<T> {

            private T object;

            private final Consumer<? super T> disposer;

            private int rc;

            private Production(T object, Consumer<? super T> disposer) {
                super();
                this.object = object;
                this.disposer = Objects.requireNonNull(disposer, "disposer");
            }

            private int reference() {
                return ++this.rc;
            }

            private int unreference() {
                int rc = --this.rc;
                if (rc == 0) {
                    this.disposeIfUnreferenced();
                } else if (rc < 0) {
                    throw new AssertionError("Unexpected negative reference count: " + rc);
                }
                return rc;
            }

            private boolean disposeIfUnreferenced() {
                if (this.rc == 0) {
                    this.dispose();
                    return true;
                } else {
                    assert this.rc > 0 : "Unexpected negative reference count: " + this.rc;
                }
                return false;
            }

            void dispose() {
                T t = this.object;
                this.object = null;
                this.disposer.accept(t);
            }

        }

        private static final record ProductionId(Set<Type> types, Set<Annotation> qualifiers) {}

    }

}
