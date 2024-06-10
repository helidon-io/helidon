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

import java.util.function.Predicate;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.hibernate.bytecode.spi.BytecodeProvider;

/**
 * In native image, we force the usage of the no-op bytecode provider so no bytecode
 * operation happen during runtime.
 */
@TargetClass(className = "org.hibernate.bytecode.internal.BytecodeProviderInitiator",
        onlyWith = BytecodeProviderInitiator.SubstituteOnlyIfPresent.class)
final class BytecodeProviderInitiator {

    private BytecodeProviderInitiator() {
    }

    @Substitute
    public static BytecodeProvider buildBytecodeProvider(String providerName) {
        return new org.hibernate.bytecode.internal.none.BytecodeProviderImpl();
    }

    static class SubstituteOnlyIfPresent implements Predicate<String> {

        @Override
        public boolean test(String type) {
            try {
                Class<?> clazz = Class.forName(type, false, getClass().getClassLoader());
                clazz.getDeclaredMethod("buildBytecodeProvider", String.class);
                return true;
            } catch (ClassNotFoundException | NoClassDefFoundError | NoSuchMethodException ex) {
                return false;
            }
        }
    }
}
