/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.graal.nativeimage.extension;

import java.util.Map;

import com.oracle.svm.core.annotate.AutomaticFeature;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.RuntimeReflection;

/**
 * Register Helidon required reflection.
 */
@AutomaticFeature
public class HelidonSvmFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // needed for YAML parsing
        RuntimeReflection.register(Map.class);
        RuntimeReflection.register(Map.class.getDeclaredMethods());
        RuntimeReflection.register(Map.class.getMethods());

        // web server - Netty
        registerInstantiation(NioServerSocketChannel.class);
    }

    private static void registerInstantiation(Class<?> aClass) {
        // can do Class.forName()
        RuntimeReflection.register(aClass);
        // can do clazz.newInstance()
        RuntimeReflection.registerForReflectiveInstantiation(aClass);
    }
}
