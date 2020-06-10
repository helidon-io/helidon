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
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import org.jboss.weld.logging.BeanLogger;
import org.jboss.weld.serialization.spi.ProxyServices;

class HelidonProxyServices implements ProxyServices {
    // a cache of all classloaders (this should be empty in most cases, as we use a single class loader in Helidon)
    private final Map<ClassLoader, ClassDefiningCl> classLoaders = Collections.synchronizedMap(new IdentityHashMap<>());
    private final ClassLoader contextCl;
    private final ClassDefiningCl contextClassDefiningCl;
    private final Module myModule;

    HelidonProxyServices() {
        // cache classloader and module of this class
        this.contextCl = HelidonProxyServices.class.getClassLoader();
        this.contextClassDefiningCl = new ClassDefiningCl(contextCl);
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

        if (samePackage(originalClass, className)) {
            // when we need to define a class in the same package (to see package local fields and methods)
            // we cannot use a classloader, as the new class would be in the same package, but in a different
            // classloader, preventing it from seeing these fields/methods
            return defineClassSamePackage(originalClass, className, classBytes, off, len);
        } else {
            // use a custom classloader to define classes in a new package
            return wrapCl(originalClass.getClassLoader()).doDefineClass(className, classBytes, off, len);
        }
    }

    @Override
    public Class<?> defineClass(Class<?> originalClass,
                                String className,
                                byte[] classBytes,
                                int off,
                                int len,
                                ProtectionDomain protectionDomain) throws ClassFormatError {

        if (samePackage(originalClass, className)) {
            return defineClassSamePackage(originalClass, className, classBytes, off, len);
        } else {
            return wrapCl(originalClass.getClassLoader()).doDefineClass(className, classBytes, off, len, protectionDomain);
        }
    }

    @Override
    public Class<?> loadClass(Class<?> originalClass, String classBinaryName) throws ClassNotFoundException {
        return wrapCl(originalClass.getClassLoader()).loadClass(classBinaryName);
    }

    private Class<?> defineClassSamePackage(Class<?> originalClass, String className, byte[] classBytes, int off, int len) {
        try {
            Module classModule = originalClass.getModule();
            if (!myModule.canRead(classModule)) {
                // we need to read the module to be able to create a private lookup in it
                // it also needs to open the package we are doing the lookup in
                myModule.addReads(classModule);
            }
            // next line would fail if the module does not open its package, with a very meaningful error message
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(originalClass, MethodHandles.lookup());
            if (classBytes.length == len) {
                return lookup.defineClass(classBytes);
            } else {
                byte[] bytes = new byte[len];
                System.arraycopy(classBytes, off, bytes, 0, len);
                return lookup.defineClass(bytes);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to define class " + className, e);
        }
    }

    private boolean samePackage(Class<?> originalClass, String className) {
        String origPackage = originalClass.getPackageName();
        String newPackage = packageName(className);
        return newPackage.equals(origPackage);
    }

    private String packageName(String className) {
        int index = className.lastIndexOf('.');
        if (index > 0) {
            return className.substring(0, index);
        }
        return "";
    }

    private ClassDefiningCl wrapCl(ClassLoader origCl) {
        if (origCl instanceof ClassDefiningCl) {
            return (ClassDefiningCl) origCl;
        }
        if (origCl == contextCl) {
            return contextClassDefiningCl;
        }
        return classLoaders.computeIfAbsent(origCl, ClassDefiningCl::new);
    }

    // a classloader that exposes define class methods
    private static class ClassDefiningCl extends ClassLoader {
        protected ClassDefiningCl(ClassLoader parent) {
            super(parent);
        }

        Class<?> doDefineClass(String className, byte[] bytes, int off, int len) {
            try {
                // avoid duplicate attempts to define a class
                return super.loadClass(className);
            } catch (ClassNotFoundException e) {
                return super.defineClass(className, bytes, off, len);
            }
        }

        Class<?> doDefineClass(String className, byte[] bytes, int off, int len, ProtectionDomain domain) {
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
