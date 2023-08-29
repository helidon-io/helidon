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

package io.helidon.integrations.oci.tls.certificates;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.Provider;

class OciTestUtils {
    static final String SUN_JSSE_PROVIDER_CLASSNAME = "com.sun.net.ssl.internal.ssl.Provider";
    static final String JIPHER_JCE_PROVIDER_NAME = "JipherJCE";

    static volatile boolean loaded;

    static boolean ociRealUsage() {
        return Boolean.getBoolean("oci.real.usage");
    }

    static void init() {
        if (!ociRealUsage()) {
            return;
        }

        if (!loaded) {
            loaded = true;

//            setupBC();
//            setupJipher();
        }
    }

    static void setupBC() {
        // OCI uses BC, we need it for decryptAesKey
        // https://stackoverflow.com/a/23859386/626826
        // https://bugs.openjdk.org/browse/JDK-7038158
//        Security.addProvider(new BouncyCastleProvider());
    }

//    static void setupJipher() {
//        // For Java 9 or later, if your application uses a JKS format truststore, such as the default
//        // Java truststore, set the javax.net.ssl.trustStoreType system property to jks. See the Jipher
//        // Troubleshooting Guide for more details. Otherwise, remove this line if the truststore used by
//        // the application is in PKCS12 format or if truststore is not used at all.
//        System.setProperty("javax.net.ssl.trustStoreType", "jks");
//
//        // Insert JipherJCE at the top of the provider list
//        Security.insertProviderAt(new com.oracle.jipher.provider.JipherJCE(), 1);
//
//        if (isClassFound(SUN_JSSE_PROVIDER_CLASSNAME)) {
//            // Replace default SunJSSE provider with an instance of the SunJSSE provider
//            // configured to use JipherJCE as its sole FIPS JCE provider.
//            Security.removeProvider("SunJSSE");
//
//            final Provider jsseProvider =
//                    createJsseProvider(SUN_JSSE_PROVIDER_CLASSNAME, JIPHER_JCE_PROVIDER_NAME);
//
//            // When the SunJSSE provider is configured with a FIPS JCE provider
//            // it does not add an alias from SSL > TLS. Add one for backwards compatibility.
//            jsseProvider.put("Alg.Alias.SSLContext.SSL", "TLS");
//
//            // Re-insert SunJSSE provider that uses JipherJCE on the 2nd position right after JipherJCE
//            // that was added above
//            Security.insertProviderAt(jsseProvider, 2);
//        }
//    }

    private static boolean isClassFound(String className) {
        try {
            Class.forName(className, false, null);
        } catch (ClassNotFoundException e) {
            return false;
        }
        return true;
    }

    private static Provider createJsseProvider(String className,
                                               String providerName) {
        try {
            final Class<?> jsseClass = Class.forName(className);
            final Constructor<?> constructor = jsseClass.getConstructor(String.class);
            return (Provider) constructor.newInstance(providerName);
        } catch (ClassNotFoundException
                 | SecurityException
                 | InstantiationException
                 | InvocationTargetException
                 | NoSuchMethodException
                 | IllegalAccessException e) {
            throw new IllegalStateException("Unable to find the provider class : " + className, e);
        }
    }

}
