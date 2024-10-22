/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.function.Predicate;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.hibernate.bytecode.spi.ReflectionOptimizer;
import org.hibernate.property.access.spi.PropertyAccess;

/**
 * Reflection optimizer of the no-op Bytecode provider cannot be disabled
 * with properties and throw an exception, so it has to return {@code null}.
 * After Hibernate version 6.5, this substitution is not required anymore.
 */
@TargetClass(className = "org.hibernate.bytecode.internal.none.BytecodeProviderImpl",
        onlyWith = BytecodeProvider.SubstituteOnlyIfPresent.class)
final class BytecodeProvider {

    private BytecodeProvider() {
    }

    @Substitute
    @SuppressWarnings("NullAway")
    public ReflectionOptimizer getReflectionOptimizer(Class<?> clazz, Map<String, PropertyAccess> propertyAccessMap) {
        return null;
    }

    static class SubstituteOnlyIfPresent implements Predicate<String> {

        @Override
        public boolean test(String type) {
            try {
                Class<?> clazz = Class.forName(type, false, getClass().getClassLoader());
                clazz.getDeclaredMethod("getReflectionOptimizer", Class.class, Map.class);
                return true;
            } catch (ClassNotFoundException | NoClassDefFoundError | NoSuchMethodException ex) {
                return false;
            }
        }
    }
}
