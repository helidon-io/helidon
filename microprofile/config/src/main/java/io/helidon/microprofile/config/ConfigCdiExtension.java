/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
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
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.ProcessObserverMethod;
import javax.enterprise.inject.spi.WithAnnotations;

import io.helidon.common.NativeImageHelper;
import io.helidon.config.ConfigException;
import io.helidon.config.mp.MpConfig;
import io.helidon.config.mp.MpConfigProviderResolver;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * Extension to enable config injection in CDI container (all of {@link io.helidon.config.Config},
 * {@link org.eclipse.microprofile.config.Config} and {@link ConfigProperty}).
 */
public class ConfigCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(ConfigCdiExtension.class.getName());
    private static final Pattern SPLIT_PATTERN = Pattern.compile("(?<!\\\\),");
    private static final Pattern ESCAPED_COMMA_PATTERN = Pattern.compile("\\,", Pattern.LITERAL);
    private static final Annotation CONFIG_PROPERTY_LITERAL = new ConfigPropertyLiteral();
    // we must do manual boxing of primitive types, to make sure the injection points match
    // the producers
    private static final Map<Class<?>, Class<?>> REPLACED_TYPES = new HashMap<>();

    static {
        // this code is duplicated in mapper manager in Config
        REPLACED_TYPES.put(byte.class, Byte.class);
        REPLACED_TYPES.put(short.class, Short.class);
        REPLACED_TYPES.put(int.class, Integer.class);
        REPLACED_TYPES.put(long.class, Long.class);
        REPLACED_TYPES.put(float.class, Float.class);
        REPLACED_TYPES.put(double.class, Double.class);
        REPLACED_TYPES.put(boolean.class, Boolean.class);
        REPLACED_TYPES.put(char.class, Character.class);
    }

    private final List<InjectionPoint> ips = new LinkedList<>();
    private final Map<Class<?>, ConfigBeanDescriptor> configBeans = new HashMap<>();

    /**
     * Constructor invoked by CDI container.
     */
    public ConfigCdiExtension() {
        LOGGER.fine("ConfigCdiExtension instantiated");
    }

    private void harvestConfigInjectionPointsFromEnabledBean(@Observes ProcessBean<?> event) {
        Bean<?> bean = event.getBean();
        Set<InjectionPoint> beanInjectionPoints = bean.getInjectionPoints();
        if (beanInjectionPoints != null) {
            for (InjectionPoint beanInjectionPoint : beanInjectionPoints) {
                if (beanInjectionPoint != null) {
                    Set<Annotation> qualifiers = beanInjectionPoint.getQualifiers();
                    assert qualifiers != null;
                    for (Annotation qualifier : qualifiers) {
                        if (qualifier instanceof ConfigProperty) {
                            ips.add(beanInjectionPoint);
                        }
                    }
                }
            }
        }
    }

    private void processAnnotatedType(@Observes @WithAnnotations(ConfigProperties.class) ProcessAnnotatedType<?> event) {
        AnnotatedType<?> annotatedType = event.getAnnotatedType();
        ConfigProperties configProperties = annotatedType.getAnnotation(ConfigProperties.class);
        if (configProperties == null) {
            // ignore classes that do not have this annotation on class level
            return;
        }
        configBeans.put(annotatedType.getJavaClass(), ConfigBeanDescriptor.create(annotatedType, configProperties));
        // we must veto this annotated type, as we need to create a custom bean to create an instance
        event.veto();
    }

    private <X> void harvestConfigPropertyInjectionPointsFromEnabledObserverMethod(@Observes ProcessObserverMethod<?, X> event,
                                                                                   BeanManager beanManager) {
        AnnotatedMethod<X> annotatedMethod = event.getAnnotatedMethod();
        List<AnnotatedParameter<X>> annotatedParameters = annotatedMethod.getParameters();
        if (annotatedParameters != null) {
            for (AnnotatedParameter<?> annotatedParameter : annotatedParameters) {
                if ((annotatedParameter != null)
                        && !annotatedParameter.isAnnotationPresent(Observes.class)) {
                    InjectionPoint injectionPoint = beanManager.createInjectionPoint(annotatedParameter);
                    Set<Annotation> qualifiers = injectionPoint.getQualifiers();
                    assert qualifiers != null;
                    for (Annotation qualifier : qualifiers) {
                        if (qualifier instanceof ConfigProperty) {
                            ips.add(injectionPoint);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Register a config producer bean for each {@link org.eclipse.microprofile.config.inject.ConfigProperty} injection.
     *
     * @param abd event from CDI container
     */
    private void registerConfigProducer(@Observes AfterBeanDiscovery abd) {
        // we also must support injection of Config itself
        abd.addBean()
                .addTransitiveTypeClosure(org.eclipse.microprofile.config.Config.class)
                .beanClass(org.eclipse.microprofile.config.Config.class)
                .scope(ApplicationScoped.class)
                .createWith(creationalContext -> new SerializableConfig());

        abd.addBean()
                .addTransitiveTypeClosure(io.helidon.config.Config.class)
                .beanClass(io.helidon.config.Config.class)
                .scope(ApplicationScoped.class)
                .createWith(creationalContext -> {
                    Config config = ConfigProvider.getConfig();
                    if (config instanceof io.helidon.config.Config) {
                        return config;
                    } else {
                        return MpConfig.toHelidonConfig(config);
                    }
                });

        Set<Type> types = ips.stream()
                .map(InjectionPoint::getType)
                .map(it -> {
                    if (it instanceof Class) {
                        Class<?> clazz = (Class<?>) it;
                        if (clazz.isPrimitive()) {
                            return REPLACED_TYPES.getOrDefault(clazz, clazz);
                        }
                    }
                    return it;
                })
                .collect(Collectors.toSet());

        types.forEach(type -> abd.addBean()
                .addType(type)
                .scope(Dependent.class)
                .addQualifier(CONFIG_PROPERTY_LITERAL)
                .produceWith(it -> produce(it.select(InjectionPoint.class).get())));

        configBeans.values().forEach(beanDescriptor -> abd.addBean()
                .addType(beanDescriptor.type())
                .addTransitiveTypeClosure(beanDescriptor.type())
                // it is non-binding
                .qualifiers(ConfigProperties.Literal.NO_PREFIX)
                .scope(Dependent.class)
                .produceWith(it -> beanDescriptor.produce(it.select(InjectionPoint.class).get(), ConfigProvider.getConfig())));
    }

    /**
     * Validate all injection points are valid.
     *
     * @param add event from CDI container
     */
    private void validate(@Observes AfterDeploymentValidation add, BeanManager beanManager) {
        CreationalContext<?> cc = beanManager.createCreationalContext(null);
        try {
            ips.forEach(ip -> {
                try {
                    beanManager.getInjectableReference(ip, cc);
                } catch (NoSuchElementException e) {
                    add.addDeploymentProblem(new ConfigException("Failed to validate injection point: " + ip, e));
                } catch (Exception e) {
                    add.addDeploymentProblem(e);
                }
            });
        } finally {
            cc.release();
        }
        ips.clear();
    }

    private Object produce(InjectionPoint ip) {
        ConfigProperty annotation = ip.getAnnotated().getAnnotation(ConfigProperty.class);
        String injectedName = injectedName(ip);
        String fullPath = ip.getMember().getDeclaringClass().getName()
                + "." + injectedName;
        String configKey = configKey(annotation, fullPath);

        if (NativeImageHelper.isBuildTime()) {
            // this is build-time of native-image - e.g. run from command line or maven
            // logging may not be working/configured to deliver this message as it should
            System.err.println("You are accessing configuration key '" + configKey + "' during"
                                       + " container initialization. This will not work nicely with Graal native-image");
        }

        Type type = ip.getType();
        return produce(configKey, type, defaultValue(annotation));
    }

    private Object produce(String configKey, Type type, String defaultValue) {
        /*
             Supported types
             group x:
                primitive types + String
                any java class (except for parameterized types)
             group y = group x + the ones listed here:
                List<x> - where x is one of the above
                x[] - where x is one of the above

             group z:
                Provider<y>
                Optional<y>
                Supplier<y>

             group z':
                Map<String, String> - a detached key/value mapping of whole subtree
             */
        FieldTypes fieldTypes = FieldTypes.create(type);
        org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
        if (config instanceof MpConfigProviderResolver.ConfigDelegate) {
            // get the actual instance to have access to Helidon specific methods
            config = config.unwrap(MpConfigProviderResolver.ConfigDelegate.class).delegate();
        }
        Object value = configValue(config, fieldTypes, configKey, defaultValue);

        if (null == value) {
            throw new NoSuchElementException("Cannot find value for key: " + configKey + " of type: " + type);
        }
        return value;
    }

    private Object configValue(Config config, FieldTypes fieldTypes, String configKey, String defaultValue) {
        Class<?> type0 = fieldTypes.field0().rawType();
        Class<?> type1 = fieldTypes.field1().rawType();
        Class<?> type2 = fieldTypes.field2().rawType();
        if (type0.equals(type1)) {
            // not a generic
            return withDefault(config, configKey, type0, defaultValue, true);
        }

        // generic declaration
        return parameterizedConfigValue(config,
                                        configKey,
                                        defaultValue,
                                        type0,
                                        type1,
                                        type2);
    }

    private static <T> T withDefault(Config config, String key, Class<T> type, String configuredDefault, boolean required) {
        String defaultValue = (configuredDefault == null || configuredDefault.isEmpty()) ? null : configuredDefault;
        // our type may be one of the explicit optionals
        if (OptionalInt.class.equals(type)) {
            return type.cast(config.getOptionalValue(key, Integer.class)
                                     .map(OptionalInt::of)
                                     .orElseGet(OptionalInt::empty));
        } else if (OptionalLong.class.equals(type)) {
            return type.cast(config.getOptionalValue(key, Long.class)
                                     .map(OptionalLong::of)
                                     .orElseGet(OptionalLong::empty));
        } else if (OptionalDouble.class.equals(type)) {
            return type.cast(config.getOptionalValue(key, Double.class)
                                     .map(OptionalDouble::of)
                                     .orElseGet(OptionalDouble::empty));
        } else if (ConfigValue.class.equals(type)) {
            ConfigValue configValue = config.getConfigValue(key);
            if (configValue.getValue() == null) {
                configValue = new ConfigValueDefault(key, configuredDefault);
            }
            return type.cast(configValue);
        }

        // If converter returns null, we should not resolve default value
        Optional<String> stringValue = config.getOptionalValue(key, String.class);

        if (stringValue.isEmpty()) {
            return convert(key, config, defaultValue, type);
        }

        // we have a value
        Converter<T> converter = config.getConverter(type)
                .orElseThrow(() -> new IllegalArgumentException("There is no converter for type \"" + type
                        .getName() + "\""));

        T value = converter.convert(stringValue.get());
        if (value == null && required) {
            throw new NoSuchElementException(
                    "Converter returned null for a required property. This is not allowed as per section 6.4. of "
                            + "the specification. Key: " + key + ", configured value: " + stringValue + ", converter: "
                            + converter.getClass().getName());
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static <T> T convert(String key, Config config, String value, Class<T> type) {
        if (null == value) {
            return null;
        }
        if (String.class.equals(type)) {
            return (T) value;
        }

        return config.getConverter(type)
                .orElseThrow(() -> new IllegalArgumentException("Did not find converter for type "
                                                                        + type.getName()
                                                                        + ", for key "
                                                                        + key))
                .convert(value);
    }

    private static Object parameterizedConfigValue(Config config,
                                                   String configKey,
                                                   String defaultValue,
                                                   Class<?> rawType,
                                                   Class<?> typeArg,
                                                   Class<?> typeArg2) {
        if (Optional.class.isAssignableFrom(rawType)) {
            if (typeArg.equals(typeArg2)) {
                return Optional.ofNullable(withDefault(config, configKey, typeArg, defaultValue, false));
            } else {
                return Optional
                        .ofNullable(parameterizedConfigValue(config,
                                                             configKey,
                                                             defaultValue,
                                                             typeArg,
                                                             typeArg2,
                                                             typeArg2));
            }
        } else if (List.class.isAssignableFrom(rawType)) {
            return asList(config, configKey, typeArg, defaultValue);
        } else if (Supplier.class.isAssignableFrom(rawType)) {
            if (typeArg.equals(typeArg2)) {
                return (Supplier<?>) () -> withDefault(config, configKey, typeArg, defaultValue, true);
            } else {
                return (Supplier<?>) () -> parameterizedConfigValue(config,
                                                                    configKey,
                                                                    defaultValue,
                                                                    typeArg,
                                                                    typeArg2,
                                                                    typeArg2);
            }
        } else if (Map.class.isAssignableFrom(rawType)) {
            Map<String, String> result = new HashMap<>();
            config.getPropertyNames()
                    .forEach(name -> {
                        // workaround for race condition (if key disappears from source after we call getPropertyNames
                        config.getOptionalValue(name, String.class).ifPresent(value -> result.put(name, value));
                    });
            return result;
        } else if (Set.class.isAssignableFrom(rawType)) {
            return new LinkedHashSet<>(asList(config, configKey, typeArg, defaultValue));
        } else {
            throw new IllegalArgumentException("Cannot create config property for " + rawType + "<" + typeArg + ">, key: "
                                                       + configKey);
        }
    }

    static String[] toArray(String stringValue) {
        String[] values = SPLIT_PATTERN.split(stringValue, -1);

        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            values[i] = ESCAPED_COMMA_PATTERN.matcher(value).replaceAll(Matcher.quoteReplacement(","));
        }
        return values;
    }

    private static <T> List<T> asList(Config config, String configKey, Class<T> typeArg, String defaultValue) {
        // first try to see if we have a direct value
        Optional<String> optionalValue = config.getOptionalValue(configKey, String.class);
        if (optionalValue.isPresent()) {
            return toList(configKey, config, optionalValue.get(), typeArg);
        }

        /*
         we also support indexed value
         e.g. for key "my.list" you can have both:
         my.list=12,13,14
         or (not and):
         my.list.0=12
         my.list.1=13
         */

        String indexedConfigKey = configKey + ".0";
        optionalValue = config.getOptionalValue(indexedConfigKey, String.class);
        if (optionalValue.isPresent()) {
            List<T> result = new LinkedList<>();

            // first element is already in
            result.add(convert(indexedConfigKey, config, optionalValue.get(), typeArg));

            // hardcoded limit to lists of 1000 elements
            for (int i = 1; i < 1000; i++) {
                indexedConfigKey = configKey + "." + i;
                optionalValue = config.getOptionalValue(indexedConfigKey, String.class);
                if (optionalValue.isPresent()) {
                    result.add(convert(indexedConfigKey, config, optionalValue.get(), typeArg));
                } else {
                    // finish the iteration on first missing index
                    break;
                }
            }
            return result;
        } else {
            if (null == defaultValue) {
                throw new NoSuchElementException("Missing list value for key " + configKey);
            }

            return toList(configKey, config, defaultValue, typeArg);
        }
    }

    private static <T> List<T> toList(String configKey, Config config, String stringValue, Class<T> typeArg) {
        if (stringValue.isEmpty()) {
            return List.of();
        }
        // we have a comma separated list
        List<T> result = new LinkedList<>();
        for (String value : toArray(stringValue)) {
            result.add(convert(configKey, config, value, typeArg));
        }
        return result;
    }

    private String defaultValue(ConfigProperty annotation) {
        String defaultFromAnnotation = annotation.defaultValue();
        if (defaultFromAnnotation.equals(ConfigProperty.UNCONFIGURED_VALUE)) {
            return null;
        }
        return defaultFromAnnotation;
    }

    private String configKey(ConfigProperty annotation, String fullPath) {
        String keyFromAnnotation = annotation.name();

        if (keyFromAnnotation.isEmpty()) {
            return fullPath.replace('$', '.');
        }
        return keyFromAnnotation;
    }

    private static String injectedName(InjectionPoint ip) {
        Annotated annotated = ip.getAnnotated();
        if (annotated instanceof AnnotatedField) {
            AnnotatedField<?> f = (AnnotatedField<?>) annotated;
            return f.getJavaMember().getName();
        }

        if (annotated instanceof AnnotatedParameter) {
            AnnotatedParameter<?> p = (AnnotatedParameter<?>) annotated;

            Member member = ip.getMember();
            if (member instanceof Method) {
                return member.getName() + "_" + p.getPosition();
            }
            if (member instanceof Constructor) {
                return "new_" + p.getPosition();
            }
        }

        return ip.getMember().getName();
    }

}
