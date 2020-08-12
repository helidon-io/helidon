/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.cdi;

import java.lang.invoke.MethodHandles;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.NativeImageHelper;

import org.jboss.weld.logging.BeanLogger;
import org.jboss.weld.serialization.spi.ProxyServices;

class HelidonProxyServices implements ProxyServices {
    private static final Logger LOGGER = Logger.getLogger(HelidonProxyServices.class.getName());
    private static final String WELD_JAVAX_PREFIX = "org.jboss.weldx.";
    private static final String WELD_JAVA_PREFIX = "org.jboss.weld.";

    // a cache of all classloaders (this should be empty in most cases, as we use a single class loader in Helidon)
    private final Map<ClassLoader, ClassDefiningCl> classLoaders = Collections.synchronizedMap(new IdentityHashMap<>());
    private final ClassLoader contextCl;
    private final ClassDefiningCl contextClassDefiningCl;
    private final Module myModule;

    HelidonProxyServices() {
        // cache classloader and module of this class
        this.contextCl = HelidonProxyServices.class.getClassLoader();
        this.contextClassDefiningCl = createCl(contextCl);
        this.myModule = HelidonProxyServices.class.getModule();
    }

    @Override
    public ClassLoader getClassLoader(Class<?> proxiedBeanType) {
        return wrapCl(proxiedBeanType.getClassLoader());
    }

    @Override
    public Class<?> loadBeanClass(String className) {
        return contextClassDefiningCl.doLoad(className);
    }

    @Override
    public void cleanup() {
        classLoaders.clear();
    }

    @Override
    public boolean supportsClassDefining() {
        return true;
    }

    @Override
    public Class<?> defineClass(Class<?> originalClass, String className, byte[] classBytes, int off, int len)
            throws ClassFormatError {

        if (weldInternalProxyClassName(className)) {
            // this is special case - these classes are defined in a non-existent package
            // and we need to use a classloader (public lookup will not allow this, and private lookup is not
            // possible for an empty package)
            return wrapCl(originalClass.getClassLoader())
                    .doDefineClass(originalClass, className, classBytes, off, len);
        }
        // any other class should be defined using a private lookup
        try {
            return defineClassPrivateLookup(originalClass, className, classBytes, off, len);
        } catch (Exception e) {
            LOGGER.log(Level.FINEST, "Failed to create class " + className + " using private lookup", e);

            throw e;
        }
    }

    @Override
    public Class<?> defineClass(Class<?> originalClass,
                                String className,
                                byte[] classBytes,
                                int off,
                                int len,
                                ProtectionDomain protectionDomain) throws ClassFormatError {

        if (weldInternalProxyClassName(className)) {
            // this is special case - these classes are defined in a non-existent package
            // and we need to use a classloader (public lookup will not allow this, and private lookup is not
            // possible for an empty package)
            return wrapCl(originalClass.getClassLoader())
                    .doDefineClass(originalClass, className, classBytes, off, len, protectionDomain);
        }
        // any other class should be defined using a private lookup
        try {
            return defineClassPrivateLookup(originalClass, className, classBytes, off, len);
        } catch (Exception e) {
            LOGGER.log(Level.FINEST, "Failed to create class " + className + " using private lookup", e);

            throw e;
        }
    }

    @Override
    public Class<?> loadClass(Class<?> originalClass, String classBinaryName) throws ClassNotFoundException {
        return wrapCl(originalClass.getClassLoader()).loadClass(classBinaryName);
    }

    private boolean weldInternalProxyClassName(String className) {
        return className.startsWith(WELD_JAVAX_PREFIX) || className.startsWith(WELD_JAVA_PREFIX);
    }

    private Class<?> defineClassPrivateLookup(Class<?> originalClass, String className, byte[] classBytes, int off, int len) {
        if (NativeImageHelper.isRuntime()) {
            throw new IllegalStateException("Cannot define class in native image. Class name: " + className + ", original "
                                                    + "class: " + originalClass
                    .getName());
        }

        LOGGER.finest("Defining class " + className + " original class: " + originalClass.getName());

        MethodHandles.Lookup lookup;

        try {
            // lookup class name "guessed" from the class name of the proxy
            String lookupClassName;

            if (className.contains("$")) {
                // package + first name in the compound proxy class name
                lookupClassName = className.substring(0, className.indexOf('$'));
            } else {
                lookupClassName = className;
            }

            // I would like to create a private lookup in the same package as the proxied class, so let's do it
            // first if the producer class and the lookup class name is the same, just use the existing class
            Class<?> lookupClass = lookupClassName.equals(originalClass.getName()) ? originalClass : null;

            ClassLoader cl = originalClass.getClassLoader();

            if (null == lookupClass) {
                // try to load the full class name
                lookupClass = tryLoading(cl, lookupClassName);
            }

            if (null == lookupClass) {
                // and if that fails, just use the bean producer class
                lookupClass = originalClass;
            }

            Module lookupClassModule = lookupClass.getModule();
            if (!myModule.canRead(lookupClassModule)) {
                // we need to read the module to be able to create a private lookup in it
                // it also needs to open the package we are doing the lookup in
                myModule.addReads(lookupClassModule);
            }

            // next line would fail if the module does not open its package, with a very meaningful error message
            lookup = MethodHandles.privateLookupIn(lookupClass, MethodHandles.lookup());
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to define class " + className, e);
        }

        return defineClass(lookup, className, classBytes, off, len);
    }

    private Class<?> tryLoading(ClassLoader cl, String className) {
        try {
            return cl.loadClass(className);
        } catch (Exception e) {
            LOGGER.log(Level.FINEST, "Attempt to load class " + className + " failed.", e);
        }
        return null;
    }

    private Class<?> defineClass(MethodHandles.Lookup lookup, String className, byte[] classBytes, int off, int len) {
        try {
            byte[] definitionBytes;

            if (classBytes.length == len) {
                definitionBytes = classBytes;
            } else {
                definitionBytes = new byte[len];
                System.arraycopy(classBytes, off, definitionBytes, 0, len);
            }

            return lookup.defineClass(definitionBytes);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to define class " + className, e);
        }
    }

    private ClassDefiningCl wrapCl(ClassLoader origCl) {
        if (origCl instanceof ClassDefiningCl) {
            return (ClassDefiningCl) origCl;
        }
        if (origCl == contextCl) {
            return contextClassDefiningCl;
        }
        return classLoaders.computeIfAbsent(origCl, this::createCl);
    }

    private ClassDefiningCl createCl(ClassLoader parent) {
        return AccessController.doPrivileged((PrivilegedAction<ClassDefiningCl>) () -> new ClassDefiningCl(parent));
    }

    // a classloader that exposes define class methods
    private static class ClassDefiningCl extends ClassLoader {
        protected ClassDefiningCl(ClassLoader parent) {
            super(parent);
        }

        Class<?> doDefineClass(Class<?> originalClass, String className, byte[] bytes, int off, int len) {
            if (NativeImageHelper.isRuntime()) {
                throw new IllegalStateException("Cannot define class in native image. Class name: " + className + ", original "
                                                        + "class: " + originalClass
                        .getName());
            }

            LOGGER.finest("Defining class " + className + " original class: " + originalClass.getName());

            try {
                // avoid duplicate attempts to define a class
                return super.loadClass(className);
            } catch (ClassNotFoundException e) {
                return super.defineClass(className, bytes, off, len);
            }
        }

        Class<?> doDefineClass(Class<?> originalClass,
                               String className,
                               byte[] bytes,
                               int off,
                               int len,
                               ProtectionDomain domain) {
            if (NativeImageHelper.isRuntime()) {
                throw new IllegalStateException("Cannot define class in native image. Class name: " + className + ", original "
                                                        + "class: " + originalClass
                        .getName());
            }

            LOGGER.finest("Defining class " + className + " original class: " + originalClass.getName());

            try {
                // avoid duplicate attempts to define a class
                return super.loadClass(className);
            } catch (ClassNotFoundException e) {
                return super.defineClass(className, bytes, off, len, domain);
            }
        }

        Class<?> doLoad(String className) {
            try {
                return super.loadClass(className, true);
            } catch (ClassNotFoundException e) {
                throw BeanLogger.LOG.cannotLoadClass(className, e);
            }
        }
    }
}
