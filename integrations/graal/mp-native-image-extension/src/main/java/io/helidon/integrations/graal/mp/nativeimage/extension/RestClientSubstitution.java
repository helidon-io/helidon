/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.graal.mp.nativeimage.extension;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.function.BooleanSupplier;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Substitution for Method handles used in original class.
 */
public class RestClientSubstitution {
    @TargetClass(className = "org.glassfish.jersey.microprofile.restclient.ReflectionUtil",
                 onlyWith = OnlyWhenOnClasspath.class)
    static final class ReflectionUtilSubstitution {
        @SuppressWarnings("unchecked")
        @Substitute
        static <T> T createProxyInstance(Class<T> restClientClass) {
            return AccessController.doPrivileged(new ProxyPrivilegedAction<>(restClientClass));
        }
    }

    /**
     * Only for native image.
     * @param <T> type
     */
    public static class ProxyPrivilegedAction<T> implements PrivilegedAction<T> {
        private final Class<T> proxyInterface;

        /**
         * Only for native image.
         * @param proxyInterface never call directly
         */
        public ProxyPrivilegedAction(Class<T> proxyInterface) {
            this.proxyInterface = proxyInterface;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T run() {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            return (T) Proxy.newProxyInstance(cl,
                                              new Class[] {proxyInterface},
                                              new DefaultMethodProxyHandler());
        }
    }

    /**
     * Only for native image.
     */
    public static class DefaultMethodProxyHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            throw new UnsupportedOperationException("Default methods are not supported within native image. Method: " + method);
        }
    }

    /**
     * Only for native image.
     */
    public static class OnlyWhenOnClasspath implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            try {
                Class.forName("org.glassfish.jersey.microprofile.restclient.ReflectionUtil");
                return true;
            } catch (Throwable e) {
                return false;
            }
        }
    }
}

