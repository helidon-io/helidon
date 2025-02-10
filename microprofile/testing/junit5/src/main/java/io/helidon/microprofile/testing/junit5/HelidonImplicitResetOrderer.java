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
package io.helidon.microprofile.testing.junit5;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import io.helidon.microprofile.testing.HelidonTestInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.ClassInfo;
import io.helidon.microprofile.testing.HelidonTestInfo.MethodInfo;

import org.junit.jupiter.api.MethodDescriptor;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.MethodOrdererContext;

/**
 * A method orderer that orders the methods that requires their own container last.
 * The resulting ordering groups the methods that share the same container in order to avoid a restart.
 *
 * @see MethodInfo#requiresReset()
 */
public class HelidonImplicitResetOrderer implements MethodOrderer {

    @Override
    public void orderMethods(MethodOrdererContext context) {
        sort(context.getMethodDescriptors());
    }

    private static <T extends MethodDescriptor> void sort(List<T> descriptors) {
        List<T> shared = new ArrayList<>();
        List<T> exclusive = new ArrayList<>();
        for (T e : descriptors) {
            Method method = e.getMethod();
            ClassInfo classInfo = HelidonTestInfo.classInfo(method.getDeclaringClass(), HelidonTestDescriptorImpl::new);
            MethodInfo methodInfo = HelidonTestInfo.methodInfo(method, classInfo, HelidonTestDescriptorImpl::new);
            // only update order for implicit reset
            if (!classInfo.resetPerTest() && methodInfo.requiresReset()) {
                exclusive.add(e);
            } else {
                shared.add(e);
            }
        }
        if (!exclusive.isEmpty()) {
            ListIterator<T> i = descriptors.listIterator();
            for (T e : shared) {
                i.next();
                i.set(e);
            }
            for (T e : exclusive) {
                i.next();
                i.set(e);
            }
        }
    }
}
