/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.svm;

import java.net.URLClassLoader;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.util.UserError;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.RuntimeReflection;

/**
 * Add all service loader files and classes.
 */
@AutomaticFeature
public class ServiceLoaderFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader instanceof URLClassLoader) {
            URLClassLoader classLoader = (URLClassLoader) contextClassLoader;

            try {
                processClassLoader(classLoader);
            } catch (ServiceFinder.ServiceFinderException e) {
                throw UserError.abort(e.getMessage(), e);
            }
        } else {
            throw UserError.abort("Context classloader is not a URLClassLoader, service manifests cannot be located");
        }

        // and also add the default JSON provider (if on classpath)
        try {
            Class<?> jsonProvider = Class.forName("org.glassfish.json.JsonProviderImpl");
            registerReflection(jsonProvider);
        } catch (ClassNotFoundException ignored) {
        }
    }

    private void processClassLoader(URLClassLoader classLoader) {
        new ServiceFinder().process(classLoader,
                                    Resources::registerResource,
                                    this::registerReflection);
    }

    private void registerReflection(Class<?> aClass) {
        RuntimeReflection.register(aClass);
        RuntimeReflection.registerForReflectiveInstantiation(aClass);
    }
}
