/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.service.jndi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceRegistry;

class RootContext implements Context {
    private static final String JAVA_COMP_PREFIX = "java:comp/";
    private static final String JNDI_HELIDON_PREFIX = "helidon:jndi/";

    private final NameParser nameParser = new NamingParser();
    private final Map<String, ChildContext> boundContexts = new HashMap<>();
    private final ReentrantReadWriteLock boundContextsLock = new ReentrantReadWriteLock();
    private final Hashtable<Object, Object> environment;
    private final Map<String, ServiceInfo> namedServices;
    private final ServiceRegistry registry;

    RootContext(Hashtable<?, ?> environment) {
        this.environment = new Hashtable<>(environment);
        ServiceRegistry registry = GlobalServiceRegistry.registry();

        Map<String, ServiceInfo> namedServices = new HashMap<>();

        List<ServiceInfo> serviceInfos = registry.lookupServices(Lookup.EMPTY);
        for (ServiceInfo serviceInfo : serviceInfos) {
            String name = findNamed(serviceInfo)
                    .orElseGet(() -> serviceInfo.serviceType().fqName());

            if (name.startsWith(JNDI_HELIDON_PREFIX)) {
                name = name.substring(JNDI_HELIDON_PREFIX.length());
            }
            namedServices.putIfAbsent(name, serviceInfo);
        }
        this.namedServices = namedServices;
        this.registry = registry;
    }

    @Override
    public Object lookup(Name name) throws NamingException {
        return lookup(name.toString());
    }

    @Override
    public Object lookup(String name) throws NamingException {
        String usedName;
        if (name.startsWith(JAVA_COMP_PREFIX)) {
            usedName = name.substring(JAVA_COMP_PREFIX.length());
        } else {
            usedName = name;
        }

        if (usedName.isBlank()) {
            return this;
        }
        boundContextsLock.readLock().lock();
        try {
            ChildContext childContext = boundContexts.get(usedName);
            if (childContext != null) {
                return childContext;
            }
        } finally {
            boundContextsLock.readLock().unlock();
        }

        ServiceInfo serviceInfo = namedServices.get(usedName);

        if (serviceInfo == null) {
            if (name.startsWith(JAVA_COMP_PREFIX)) {
                serviceInfo = namedServices.get(name);
            }
            if (serviceInfo == null) {
                throw new NamingException("There is no service registered with name " + usedName);
            }
            return serviceInfo;
        } else {
            return registry.get(serviceInfo).orElseThrow(() -> new NamingException("Service with name \""
                                                                                           + usedName
                                                                                           + "\" did not provide a value"));
        }
    }

    @Override
    public void bind(Name name, Object obj) throws NamingException {
        bind(name.toString(), obj);
    }

    @Override
    public void bind(String name, Object obj) throws NamingException {
        throw new NamingException("Helidon naming context does not support direct binding. Bind by exposing a named "
                                          + "service in the service registry. Name: " + name + ", object: " + obj);
    }

    @Override
    public void rebind(Name name, Object obj) throws NamingException {
        rebind(name.toString(), obj);
    }

    @Override
    public void rebind(String name, Object obj) throws NamingException {
        throw new NamingException("Helidon naming context does not support rebinding. Bind by exposing a named "
                                          + "service in the service registry. Name: " + name + ", object: " + obj);
    }

    @Override
    public void unbind(Name name) throws NamingException {
        unbind(name.toString());
    }

    @Override
    public void unbind(String name) throws NamingException {
        throw new NamingException("Helidon naming context does not support direct unbinding. Name: " + name);
    }

    @Override
    public void rename(Name oldName, Name newName) throws NamingException {
        rename(oldName.toString(), newName.toString());
    }

    @Override
    public void rename(String oldName, String newName) throws NamingException {
        throw new NamingException("Helidon naming context does not support renaming. Name: " + oldName + ", newName: " + newName);
    }

    @Override
    public NamingEnumeration<NameClassPair> list(Name name) {
        return list(name.toString());
    }

    @Override
    public NamingEnumeration<NameClassPair> list(String name) {
        List<NameClassPair> pairs = new ArrayList<>();

        for (var entry : namedServices.entrySet()) {
            String serviceName = entry.getKey();
            if (serviceName.startsWith(name)) {
                pairs.add(new NameClassPair(serviceName, entry.getValue().serviceType().fqName(), false));
            }
        }
        return new NamingEnum<>(pairs);
    }

    @Override
    public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
        return listBindings(name.toString());
    }

    @Override
    public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
        throw new NamingException("Helidon naming context does not support direct binding list. Name: " + name);
    }

    @Override
    public void destroySubcontext(Name name) {
    }

    @Override
    public void destroySubcontext(String name) {
    }

    @Override
    public Context createSubcontext(Name name) {
        if (name.isEmpty()) {
            return this;
        }
        var ctx = new ChildContext(this, name);

        boundContextsLock.writeLock().lock();
        try {
            boundContexts.put(name.toString(), ctx);
        } finally {
            boundContextsLock.writeLock().unlock();
        }
        return ctx;
    }

    @Override
    public Context createSubcontext(String name) throws NamingException {
        return createSubcontext(nameParser.parse(name));
    }

    @Override
    public Object lookupLink(Name name) throws NamingException {
        return lookup(name);
    }

    @Override
    public Object lookupLink(String name) throws NamingException {
        return lookup(name);
    }

    @Override
    public NameParser getNameParser(Name name) {
        return nameParser;
    }

    @Override
    public NameParser getNameParser(String name) {
        return nameParser;
    }

    @Override
    public Name composeName(Name name, Name prefix) throws NamingException {
        return nameParser.parse(composeName(name.toString(), prefix.toString()));
    }

    @Override
    public String composeName(String name, String prefix) throws NamingException {
        return prefix + "/" + name;
    }

    @Override
    public Object addToEnvironment(String propName, Object propVal) {
        return environment.put(propName, propVal);
    }

    @Override
    public Object removeFromEnvironment(String propName) {
        return environment.remove(propName);
    }

    @Override
    public Hashtable<?, ?> getEnvironment() {
        return new Hashtable<>(environment);
    }

    @Override
    public void close() {

    }

    @Override
    public String getNameInNamespace() {
        return "";
    }

    NameParser nameParser() {
        return nameParser;
    }

    private Optional<String> findNamed(ServiceInfo serviceInfo) {
        for (Qualifier qualifier : serviceInfo.qualifiers()) {
            if (qualifier.typeName().equals(Service.Named.TYPE)) {
                return Optional.of(qualifier.value().orElseThrow());
            }
        }
        return Optional.empty();
    }
}
