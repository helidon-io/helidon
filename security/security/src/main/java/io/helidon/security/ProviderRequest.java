/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.security.util.AbacSupport;

/**
 * A request sent to security providers.
 * Contains all information that may be needed to authenticate or authorize a request:
 * <ul>
 * <li>User's subject: {@link #subject()} - if user is authenticated</li>
 * <li>Service subject: {@link #service()} - if service is authenticated</li>
 * <li>Environment information: {@link #env()} - path, method etc.</li>
 * <li>Object: {@link #getObject()} - target resource, if provided by user</li>
 * <li>Security context: {@link #securityContext()} - current subjects and information about security context of this
 * request</li>
 * <li>Endpoint configuration: {@link #endpointConfig()} - annotations, endpoint specific configuration, custom objects,
 * custom atttributes</li>
 * </ul>
 */
public class ProviderRequest implements AbacSupport {
    private static final Logger LOGGER = Logger.getLogger(ProviderRequest.class.getName());

    private final Map<String, AbacSupport> contextRoot = new HashMap<>();
    private final Optional<Subject> subject;
    private final Optional<Subject> service;
    private final SecurityEnvironment env;
    private final Optional<ObjectWrapper> resource;
    private final SecurityContext context;
    private final EndpointConfig epConfig;

    ProviderRequest(SecurityContext context,
                    Map<String, Supplier<Object>> resources) {
        ObjectWrapper object = null;

        for (Map.Entry<String, Supplier<Object>> entry : resources.entrySet()) {
            ObjectWrapper wrapper = new ObjectWrapper(entry.getValue());
            contextRoot.put(entry.getKey(), wrapper);
            if ("object".equals(entry.getKey())) {
                object = wrapper;
            }
        }

        this.env = context.env();
        this.epConfig = context.endpointConfig();
        this.context = context;
        this.resource = Optional.ofNullable(object);
        this.subject = context.user();
        this.service = context.service();

        contextRoot.put("env", env);
        subject.ifPresent(user -> contextRoot.put("subject", user));
        service.ifPresent(svc -> contextRoot.put("service", svc));
    }

    /**
     * Get a value of a property from an object.
     * If object implements {@link AbacSupport} the value is obtained through {@link AbacSupport#abacAttribute(String)}, if not,
     * the value is obtained by reflection from a public field or a public getter method.
     * The method name may be (for attribute called for example "audit"):
     * <ul>
     * <li>audit</li>
     * <li>getAudit</li>
     * <li>isAudit</li>
     * <li>shouldAudit</li>
     * <li>hasAudit</li>
     * </ul>
     *
     * @param object object to get attribute from
     * @param key    key of the attribute
     * @return value of the attribute if found
     */
    public static Optional<Object> getValue(Object object, String key) {
        // use getter, public field
        // first check if a public field with the name exists
        Class<?> aClass = object.getClass();
        try {
            Field field = aClass.getField(key);
            // java9
            //if (field.canAccess(null)) {
            // java8
            if (ReflectionUtil.canAccess(ProviderRequest.class, field)) {
                return Optional.ofNullable(field.get(object));
            }
        } catch (NoSuchFieldException e) {
            // ignore, field is not present, we try accessor methods
            LOGGER.log(Level.FINEST, e, () -> "Field \"" + key + "\" + is not present in class: " + aClass.getName());
        } catch (IllegalAccessException e) {
            // ignore, we check access first
            LOGGER.log(Level.FINEST, e, () -> "Failed to access field: \"" + key + "\" in class: " + aClass.getName());
        }

        //now check accessor methods
        String capName = capitalize(key);
        return getMethod(aClass, "get" + capName)
                .or(() -> getMethod(aClass, key))
                .or(() -> getMethod(aClass, "is" + capName))
                .or(() -> getMethod(aClass, "has" + capName))
                .or(() -> getMethod(aClass, "should" + capName))
                .map(method -> {
                    try {
                        return method.invoke(object);
                    } catch (Exception e) {
                        throw new SecurityException("Failed to invoke method \"" + method + "\" on class \"" + aClass
                                .getName() + "\"", e);
                    }
                });
    }

    static String capitalize(String string) {
        char c = string.charAt(0);
        char upperCase = Character.toUpperCase(c);
        return upperCase + string.substring(1);
    }

    static Optional<Method> getMethod(Class<?> aClass, String methodName) {
        try {
            Method method = aClass.getMethod(methodName);
            // java9
            //if (method.canAccess(null)) {
            // java8 not good approach (don't have any other)
            if (ReflectionUtil.canAccess(ProviderRequest.class, method)) {
                return Optional.of(method);
            }
            return Optional.empty();
        } catch (NoSuchMethodException e) {
            // method is not present
            LOGGER.log(Level.FINEST, e, () -> "Method: \"" + methodName + "\" is not in class: " + aClass.getName());
            return Optional.empty();
        }
    }

    /**
     * Configuration of the invoked endpoint, such as annotations declared.
     * @return endpoint config
     */
    public EndpointConfig endpointConfig() {
        return epConfig;
    }

    /**
     * Security context associated with current request.
     * @return security context
     */
    public SecurityContext securityContext() {
        return context;
    }

    /**
     * Current user subject, if already authenticated.
     * @return user subject or empty
     */
    public Optional<Subject> subject() {
        return subject;
    }

    /**
     * Current service subject, if already authenticated.
     * @return service subject or empty.
     */
    public Optional<Subject> service() {
        return service;
    }

    /**
     * Environment of current request, such as the URI invoked, time to use for security decisions etc.
     * @return security environment
     */
    public SecurityEnvironment env() {
        return env;
    }

    /**
     * The object of this request. Security request may be configured for a specific entity (e.g. if this is an entity
     * modification request, the entity itself may be provided to help in a security task.
     *
     * @return the object or empty if not known
     */
    public Optional<Object> getObject() {
        return resource.map(ObjectWrapper::getValue);
    }

    @Override
    public Object abacAttributeRaw(String key) {
        return contextRoot.get(key);
    }

    @Override
    public Collection<String> abacAttributeNames() {
        return contextRoot.keySet();
    }

    private static final class ObjectWrapper implements AbacSupport {
        private final Supplier<Object> valueSupplier;
        private volatile Object value;
        private volatile AbacSupport container;

        private ObjectWrapper(Supplier<Object> value) {
            this.valueSupplier = value;
        }

        @Override
        public Object abacAttributeRaw(String key) {
            checkValue();

            if (null != container) {
                return container.abacAttributeRaw(key);
            }
            return ProviderRequest.getValue(value, key);
        }

        @Override
        public Collection<String> abacAttributeNames() {
            checkValue();

            if (null != container) {
                return container.abacAttributeNames();
            }

            throw new UnsupportedOperationException("Property names are not available for general Object types, such as: "
                                                            + value.getClass());
        }

        Object getValue() {
            checkValue();
            return value;
        }

        private void checkValue() {
            if (null == value) {
                synchronized (valueSupplier) {
                    if (null == value) {
                        value = valueSupplier.get();
                        if (value instanceof AbacSupport) {
                            container = (AbacSupport) value;
                        } else if (value instanceof Map) {
                            final Map<?, ?> map = (Map<?, ?>) value;
                            container = new AbacSupport() {
                                @Override
                                public Object abacAttributeRaw(String key) {
                                    return map.get(key);
                                }

                                @Override
                                public Collection<String> abacAttributeNames() {
                                    return map.keySet()
                                            .stream()
                                            .map(String::valueOf)
                                            .collect(Collectors.toSet());
                                }
                            };
                        }
                    }
                }
            }
        }
    }

}
