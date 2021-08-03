/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.jersey;

import javax.annotation.Priority;
import javax.inject.Singleton;
import javax.ws.rs.core.Application;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.glassfish.jersey.inject.hk2.Hk2InjectionManagerFactory;
import org.glassfish.jersey.internal.inject.Binder;
import org.glassfish.jersey.internal.inject.Binding;
import org.glassfish.jersey.internal.inject.ForeignDescriptor;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.inject.InstanceBinding;
import org.glassfish.jersey.internal.inject.ServiceHolder;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * Overrides the injection manager factory from Jersey and provides a new implementation
 * of {@code InjectionManager}. This new injection manager will separate registrations
 * for those global (shared) providers and those returned by calling {@code getClasses}.
 *
 * This separation is necessary to properly associate providers with JAX-RS applications,
 * of which there could be more than one in Helidon.
 */
@Priority(11)   // overrides Jersey's
public class HelidonHK2InjectionManagerFactory extends Hk2InjectionManagerFactory {

    @Override
    public InjectionManager create(Object parent) {
        if (parent == null) {
            return super.create(null);
        } else if (parent instanceof InjectionManagerWrapper) {
            InjectionManagerWrapper wrapper = (InjectionManagerWrapper) parent;
            return new HelidonInjectionManager(super.create(null),
                    wrapper.injectionManager, wrapper.application);
        } else {
            throw new IllegalStateException("Invalid parent injection manager");
        }
    }

    static class HelidonInjectionManager implements InjectionManager {
        private static final Logger LOGGER = Logger.getLogger(HelidonInjectionManager.class.getName());

        private Set<Class<?>> classes;
        private Set<Object> singletons;
        private final ResourceConfig resourceConfig;
        private final InjectionManager parent;
        private final InjectionManager delegate;

        HelidonInjectionManager(InjectionManager delegate, InjectionManager parent, ResourceConfig resourceConfig) {
            this.delegate = delegate;
            this.parent = parent;
            this.resourceConfig = resourceConfig;
        }

        @Override
        public void completeRegistration() {
            parent.completeRegistration();
            delegate.completeRegistration();
        }

        @Override
        public void shutdown() {
            parent.shutdown();
            delegate.shutdown();
        }

        /**
         * Registers classes returned by {@code getClasses} in {@code delegate} and
         * all other classes in {@code parent}. This is done to keep separation between
         * global providers and those that are specific to an {@code Application} class.
         *
         * @param binding the binding to register.
         */
        @Override
        public void register(Binding binding) {
            if (returnedByApplication(binding)) {
                delegate.register(binding);
                LOGGER.info("register delegate " + toString(binding));
            } else {
                parent.register(binding);
                LOGGER.info("register parent " + toString(binding));
            }
        }

        @Override
        public void register(Iterable<Binding> descriptors) {
            descriptors.forEach(this::register);
        }

        @Override
        public void register(Binder binder) {
            binder.getBindings().forEach(this::register);
        }

        @Override
        public void register(Object provider) throws IllegalArgumentException {
            LOGGER.info("Register Object " + provider);
            parent.register(provider);
        }

        @Override
        public boolean isRegistrable(Class<?> clazz) {
            return parent.isRegistrable(clazz);
        }

        @Override
        public <T> T createAndInitialize(Class<T> createMe) {
            return parent.createAndInitialize(createMe);
        }

        /**
         * Collects all service holders, including those registered in the parent and the
         * delegate.
         *
         * @param contractOrImpl contract or implementation class.
         * @param qualifiers the qualifiers.
         * @param <T> parameter type.
         * @return list of service holders.
         */
        @Override
        public <T> List<ServiceHolder<T>> getAllServiceHolders(Class<T> contractOrImpl, Annotation... qualifiers) {
            List<ServiceHolder<T>> parentList = parent.getAllServiceHolders(contractOrImpl, qualifiers);
            parentList.forEach(sh -> LOGGER.finest(() ->
                    "getAllServiceHolders parent " + sh.getContractTypes().iterator().next()));
            List<ServiceHolder<T>> delegateList = delegate.getAllServiceHolders(contractOrImpl, qualifiers);
            delegateList.forEach(sh -> LOGGER.finest(() ->
                    "getAllServiceHolders delegate " + sh.getContractTypes().iterator().next()));
            return delegateList.size() == 0 ? parentList
                    : Stream.concat(parentList.stream(), delegateList.stream()).collect(Collectors.toList());
        }

        @Override
        public <T> T getInstance(Class<T> contractOrImpl, Annotation... qualifiers) {
            T t = parent.getInstance(contractOrImpl, qualifiers);
            return t != null ? t : delegate.getInstance(contractOrImpl, qualifiers);
        }

        @Override
        public <T> T getInstance(Class<T> contractOrImpl, String classAnalyzer) {
            T t = parent.getInstance(contractOrImpl, classAnalyzer);
            return t != null ? t : delegate.getInstance(contractOrImpl, classAnalyzer);
        }

        @Override
        public <T> T getInstance(Class<T> contractOrImpl) {
            T t = parent.getInstance(contractOrImpl);
            return t != null ? t : delegate.getInstance(contractOrImpl);
        }

        @Override
        public <T> T getInstance(Type contractOrImpl) {
            T t = parent.getInstance(contractOrImpl);
            return t != null ? t : delegate.getInstance(contractOrImpl);
        }

        @Override
        public Object getInstance(ForeignDescriptor foreignDescriptor) {
            Object o = parent.getInstance(foreignDescriptor);
            return o != null ? o : delegate.getInstance(foreignDescriptor);
        }

        @Override
        public ForeignDescriptor createForeignDescriptor(Binding binding) {
            return parent.createForeignDescriptor(binding);
        }

        @Override
        public <T> List<T> getAllInstances(Type contractOrImpl) {
            return parent.getAllInstances(contractOrImpl);
        }

        @Override
        public void inject(Object injectMe) {
            parent.inject(injectMe);
        }

        @Override
        public void inject(Object injectMe, String classAnalyzer) {
            parent.inject(injectMe, classAnalyzer);
        }

        @Override
        public void preDestroy(Object preDestroyMe) {
            parent.preDestroy(preDestroyMe);
        }

        /**
         * Calls {@code getClasses} method and caches the result.
         *
         * @return set of classes returned from {@code Application} object.
         */
        private Set<Class<?>> getClasses() {
            if (classes == null) {
                Application application = resourceConfig.getApplication();
                if (application != null) {
                    classes = application.getClasses();
                }
            }
            return classes != null ? classes : Collections.EMPTY_SET;
        }

        /**
         * Calls {@code getSingletons} method and caches the result.
         *
         * @return set of singletons returned from {@code Application} object.
         */
        private Set<Object> getSingletons() {
            if (singletons == null) {
                Application application = resourceConfig.getApplication();
                if (application != null) {
                    singletons = application.getSingletons();
                }
            }
            return singletons != null ? singletons : Collections.EMPTY_SET;
        }

        /**
         * Convenience method to display a binding in logging messages.
         *
         * @param b the binding
         * @return string representation of binding
         */
        private static String toString(Binding b) {
            StringBuilder sb = new StringBuilder();
            b.getContracts().forEach(c -> sb.append(" Cont " + c));
            if (b.getImplementationType() != null) {
                sb.append("\n\tImpl " + b.getImplementationType());
            }
            return sb.toString();
        }

        /**
         * Checks if any of the contracts are returned by {@code getClasses()} or any of the
         * instances are returned by {@code getSingletons}. For a provider such as a filter,
         * the contracts will include both the class implementing the filter,
         * returned by {@code getClasses()}, as well as the JAX-RS API filter interface.
         *
         * @param binding injection manager binding
         * @return
         */
        private boolean returnedByApplication(Binding binding) {
            if (Singleton.class.equals(binding.getScope())) {
                try {
                    InstanceBinding<?> instanceBinding = (InstanceBinding<?>) binding;
                    return getSingletons().contains(instanceBinding.getService());
                } catch (Exception e) {
                    return false;
                }
            } else {
                return binding.getContracts().stream().filter(
                        c -> getClasses().contains(c)).findAny().isPresent();       // set intersection
            }
        }
    }

    /**
     * A simple {@code InjectionManager} wrapper to keep track of a resource config.
     * Methods in this class should never be called.
     */
    static class InjectionManagerWrapper implements InjectionManager {

        private final InjectionManager injectionManager;
        private final ResourceConfig application;

        InjectionManagerWrapper(InjectionManager injectionManager, ResourceConfig application) {
            this.injectionManager = injectionManager;
            this.application = application;
        }

        @Override
        public void completeRegistration() {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void register(Binding binding) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void register(Iterable<Binding> descriptors) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void register(Binder binder) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void register(Object provider) throws IllegalArgumentException {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public boolean isRegistrable(Class<?> clazz) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public <T> T createAndInitialize(Class<T> createMe) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public <T> List<ServiceHolder<T>> getAllServiceHolders(Class<T> contractOrImpl, Annotation... qualifiers) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public <T> T getInstance(Class<T> contractOrImpl, Annotation... qualifiers) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public <T> T getInstance(Class<T> contractOrImpl, String classAnalyzer) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public <T> T getInstance(Class<T> contractOrImpl) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public <T> T getInstance(Type contractOrImpl) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public Object getInstance(ForeignDescriptor foreignDescriptor) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public ForeignDescriptor createForeignDescriptor(Binding binding) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public <T> List<T> getAllInstances(Type contractOrImpl) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void inject(Object injectMe) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void inject(Object injectMe, String classAnalyzer) {
            throw new UnsupportedOperationException("Not supported");
        }

        @Override
        public void preDestroy(Object preDestroyMe) {
            throw new UnsupportedOperationException("Not supported");
        }
    }
}
