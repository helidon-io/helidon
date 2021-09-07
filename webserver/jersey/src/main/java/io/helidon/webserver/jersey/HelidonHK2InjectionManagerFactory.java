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

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Priority;
import javax.inject.Singleton;
import javax.ws.rs.core.Application;

import org.glassfish.jersey.inject.hk2.Hk2InjectionManagerFactory;
import org.glassfish.jersey.inject.hk2.ImmediateHk2InjectionManager;
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
 * for those global (shared) providers and those returned by calling {@code getClasses}
 * and {@code getSingletons}.
 *
 * This separation is necessary to properly associate providers with JAX-RS applications,
 * of which there could be more than one in Helidon.
 */
@Priority(11)   // overrides Jersey's
public class HelidonHK2InjectionManagerFactory extends Hk2InjectionManagerFactory {
    private static final Logger LOGGER = Logger.getLogger(HelidonHK2InjectionManagerFactory.class.getName());

    @Override
    public InjectionManager create(Object parent) {
        InjectionManager result;

        if (parent == null) {
            result = super.create(null);
            LOGGER.finest(() -> "Creating injection manager " + result);
        } else if (parent instanceof ImmediateHk2InjectionManager) {        // single JAX-RS app
            result = (InjectionManager) parent;
            LOGGER.finest(() -> "Using injection manager " + result);
        } else if (parent instanceof InjectionManagerWrapper) {             // multiple JAX-RS apps
            InjectionManagerWrapper wrapper = (InjectionManagerWrapper) parent;
            InjectionManager forApplication = super.create(null);
            result = new HelidonInjectionManager(forApplication, wrapper.injectionManager, wrapper.application);
            LOGGER.finest(() -> "Creating injection manager " + forApplication + " with shared "
                    + wrapper.injectionManager);
        } else {
            throw new IllegalStateException("Invalid parent injection manager");
        }
        return result;
    }

    /**
     * <p>Helidon implementation of an injection manager. Based on two underlying injection managers:
     * one to handle application specific classes (returned by the {@code Application} subclass
     * methods) and one that is shared among all the {@code Application} subclasses. Thus, if a
     * Helidon application comprises N subclasses, then N+1 injection managers will be created.</p>
     *
     * <p>Creating a separate injection manager ensures that providers associated with a certain
     * subclass are not returned for others. There will be an instance of this class for each
     * {@code Application} subclass. This manager needs to get access to the values returned
     * by {@code getClasses} and {@code getInstances} in order to provide the correct registration
     * semantics</p>
     */
    static class HelidonInjectionManager implements InjectionManager {
        private static final Logger LOGGER = Logger.getLogger(HelidonInjectionManager.class.getName());

        private final ResourceConfig resourceConfig;
        private final InjectionManager shared;
        private final InjectionManager forApplication;

        HelidonInjectionManager(InjectionManager forApplication, InjectionManager shared, ResourceConfig resourceConfig) {
            this.forApplication = forApplication;
            this.shared = shared != null ? shared : forApplication;       // for testing
            this.resourceConfig = resourceConfig;
        }

        @Override
        public void completeRegistration() {
            shared.completeRegistration();
            forApplication.completeRegistration();
        }

        @Override
        public void shutdown() {
            shared.shutdown();
            forApplication.shutdown();
        }

        /**
         * Registers classes returned by {@code getClasses} in {@code forApplication} and
         * all other classes in {@code shared}. This is done to keep separation between
         * global providers and those that are specific to an {@code Application} class.
         *
         * @param binding the binding to register.
         */
        @Override
        public void register(Binding binding) {
            if (returnedByApplication(binding)) {
                forApplication.register(binding);
                LOGGER.finest(() -> "register forApplication " + forApplication + " " + toString(binding));
            } else {
                shared.register(binding);
                LOGGER.finest(() -> "register shared " + shared + " " + toString(binding));
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
            if (getSingletons().contains(provider)) {
                forApplication.register(provider);
                LOGGER.finest(() -> "register forApplication " + forApplication + " " + provider);
            } else {
                shared.register(provider);
                LOGGER.finest(() -> "register shared " + forApplication + " " + provider);
            }
        }

        @Override
        public boolean isRegistrable(Class<?> clazz) {
            return shared.isRegistrable(clazz) || forApplication.isRegistrable(clazz);
        }

        @Override
        public <T> T createAndInitialize(Class<T> createMe) {
            try {
                return shared.createAndInitialize(createMe);
            } catch (Throwable t) {
                return forApplication.createAndInitialize(createMe);
            }
        }

        /**
         * Collects all service holders, including those registered in the {@code shared}
         * and the {@code forApplication}.
         *
         * @param contractOrImpl contract or implementation class.
         * @param qualifiers the qualifiers.
         * @param <T> parameter type.
         * @return list of service holders.
         */
        @Override
        public <T> List<ServiceHolder<T>> getAllServiceHolders(Class<T> contractOrImpl, Annotation... qualifiers) {
            List<ServiceHolder<T>> sharedList = shared.getAllServiceHolders(contractOrImpl, qualifiers);
            sharedList.forEach(sh -> LOGGER.finest(() ->
                    "getAllServiceHolders shared " + shared + " " + sh.getContractTypes().iterator().next()));
            List<ServiceHolder<T>> forApplicationList = forApplication.getAllServiceHolders(contractOrImpl, qualifiers);
            forApplicationList.forEach(sh -> LOGGER.finest(() ->
                    "getAllServiceHolders forApplication " + forApplication + " " + sh.getContractTypes().iterator().next()));
            return forApplicationList.size() == 0 ? sharedList
                    : Stream.concat(sharedList.stream(), forApplicationList.stream()).collect(Collectors.toList());
        }

        @Override
        public <T> T getInstance(Class<T> contractOrImpl, Annotation... qualifiers) {
            T t = shared.getInstance(contractOrImpl, qualifiers);
            return t != null ? t : forApplication.getInstance(contractOrImpl, qualifiers);
        }

        @Override
        public <T> T getInstance(Class<T> contractOrImpl, String classAnalyzer) {
            T t = shared.getInstance(contractOrImpl, classAnalyzer);
            return t != null ? t : forApplication.getInstance(contractOrImpl, classAnalyzer);
        }

        @Override
        public <T> T getInstance(Class<T> contractOrImpl) {
            T t = shared.getInstance(contractOrImpl);
            return t != null ? t : forApplication.getInstance(contractOrImpl);
        }

        @Override
        public <T> T getInstance(Type contractOrImpl) {
            T t = shared.getInstance(contractOrImpl);
            return t != null ? t : forApplication.getInstance(contractOrImpl);
        }

        @Override
        public Object getInstance(ForeignDescriptor foreignDescriptor) {
            Object o = shared.getInstance(foreignDescriptor);
            return o != null ? o : forApplication.getInstance(foreignDescriptor);
        }

        @Override
        public ForeignDescriptor createForeignDescriptor(Binding binding) {
            try {
                return shared.createForeignDescriptor(binding);
            } catch (Throwable t) {
                return forApplication.createForeignDescriptor(binding);
            }
        }

        @Override
        public <T> List<T> getAllInstances(Type contractOrImpl) {
            return Stream.concat(
                    shared.<T>getAllInstances(contractOrImpl).stream(),
                    forApplication.<T>getAllInstances(contractOrImpl).stream())
                    .collect(Collectors.toList());
        }

        @Override
        public void inject(Object injectMe) {
            try {
                shared.inject(injectMe);
            } catch (Throwable t) {
                LOGGER.warning(() -> "Injection failed for " + injectMe + " using shared");
                forApplication.inject(injectMe);
            }
        }

        @Override
        public void inject(Object injectMe, String classAnalyzer) {
            try {
                shared.inject(injectMe, classAnalyzer);
            } catch (Throwable t) {
                LOGGER.warning(() -> "Injection failed for " + injectMe + " using shared");
                forApplication.inject(injectMe, classAnalyzer);
            }
        }

        @Override
        public void preDestroy(Object preDestroyMe) {
            shared.preDestroy(preDestroyMe);
            forApplication.preDestroy(preDestroyMe);
        }

        /**
         * Calls {@code getClasses} method in resource config.
         *
         * @return set of classes returned from {@code Application} object.
         */
        private Set<Class<?>> getClasses() {
            Application application = resourceConfig.getApplication();
            return application != null ? resourceConfig.getClasses() : Collections.emptySet();
        }

        /**
         * Calls {@code getSingletons} method in resource config.
         *
         * @return set of singletons returned from {@code Application} object.
         */
        private Set<Object> getSingletons() {
            Application application = resourceConfig.getApplication();
            return application != null ? resourceConfig.getSingletons() : Collections.emptySet();
        }

        /**
         * Convenience method to display a binding in logging messages.
         *
         * @param b the binding
         * @return string representation of binding
         */
        @SuppressWarnings("unchecked")
        private static String toString(Binding b) {
            StringBuilder sb = new StringBuilder();
            b.getContracts().forEach(c -> sb.append(" Cont ").append(c));
            if (b.getImplementationType() != null) {
                sb.append("\n\tImpl ").append(b.getImplementationType());
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
         * @return outcome of test
         */
        @SuppressWarnings("unchecked")
        private boolean returnedByApplication(Binding binding) {
            // Check singleton binding first
            if (Singleton.class.equals(binding.getScope()) && binding instanceof InstanceBinding<?>) {
                InstanceBinding<?> instanceBinding = (InstanceBinding<?>) binding;
                return getSingletons().contains(instanceBinding.getService());
            }

            // Check any contract is returned by getClasses()
            return binding.getContracts().stream().anyMatch(c -> getClasses().contains(c));
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
