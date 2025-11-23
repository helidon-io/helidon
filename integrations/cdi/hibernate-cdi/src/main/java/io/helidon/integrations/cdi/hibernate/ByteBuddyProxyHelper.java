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

package io.helidon.integrations.cdi.hibernate;

import java.util.Collection;
import java.util.function.BiFunction;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;


@TargetClass(className = "org.hibernate.proxy.pojo.bytebuddy.ByteBuddyProxyHelper")
@SuppressWarnings("checkstyle:StaticVariableName")
final class ByteBuddyProxyHelper {

    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)
    private static TypeDescription OBJECT;

    @Substitute
    private BiFunction<ByteBuddy, NamingStrategy, DynamicType.Builder<?>> proxyBuilder(TypeDefinition persistentClass,
            Collection<? extends TypeDefinition> interfaces) {
        return null;
    }
}
