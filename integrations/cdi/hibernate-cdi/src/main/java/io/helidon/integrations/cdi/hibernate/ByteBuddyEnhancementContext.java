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

import java.util.Optional;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

@TargetClass(className = "org.hibernate.bytecode.enhance.internal.bytebuddy.ByteBuddyEnhancementContext")
@SuppressWarnings("checkstyle:StaticVariableName")
final class ByteBuddyEnhancementContext {

    @Alias
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)
    private static ElementMatcher.Junction<MethodDescription> IS_GETTER;

    @Substitute
    Optional<MethodDescription> resolveGetter(FieldDescription fieldDescription) {
        return Optional.empty();
    }
}
