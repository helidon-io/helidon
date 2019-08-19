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

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.JdkLoggerFactory;

/**
 * Substitutions for GraalVM native image generation.
 */
// suppressing checkstyle issues, as this class cannot follow usual naming rules
@SuppressWarnings({"StaticVariableName", "VisibilityModifier"})
public final class NettySubstitutions {
    // latest Netty sources now contain Graal VM native-image substitutions, so we could remove them

    @TargetClass(className = "io.netty.util.internal.logging.InternalLoggerFactory")
    static final class InternalLoggerFactorySvmExtension {
        @Substitute
        private static InternalLoggerFactory newDefaultFactory(String name) {
            return JdkLoggerFactory.INSTANCE;
        }
    }
}
