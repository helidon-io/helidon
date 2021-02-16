/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.common.configurable;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.LazyValue;

final class VirtualExecutorUtil {
    private static final Logger LOGGER = Logger.getLogger(VirtualExecutorUtil.class.getName());
    private static final LazyValue<Boolean> SUPPORTED = LazyValue.create(VirtualExecutorUtil::findSupported);
    private static final LazyValue<ExecutorService> EXECUTOR_SERVICE = LazyValue.create(VirtualExecutorUtil::findExecutor);

    private VirtualExecutorUtil() {
    }

    static boolean isVirtualSupported() {
        return SUPPORTED.get();
    }

    static ExecutorService executorService() {
        ExecutorService result = EXECUTOR_SERVICE.get();
        if (result == null) {
            throw new IllegalStateException("Virtual executor service is not supported on this JVM");
        }
        return result;
    }

    private static boolean findSupported() {
        try {
            // the method is intentionally NOT CACHED in static context, to support differences between build
            // and runtime environments (support for GraalVM native image)
            findMethod();
            return true;
        } catch (final ReflectiveOperationException e) {
            LOGGER.log(Level.FINEST, "Loom virtual executor service not available", e);
        }

        return false;
    }

    private static ExecutorService findExecutor() {
        try {
            // the method is intentionally NOT CACHED in static context, to support differences between build
            // and runtime environments (support for GraalVM native image)
            return (ExecutorService) findMethod().invoke(null);
        } catch (final ReflectiveOperationException e) {
            LOGGER.log(Level.FINEST, "Loom virtual executor service not available", e);
        }

        return null;
    }

    private static Method findMethod() throws ReflectiveOperationException{
        return Executors.class.getDeclaredMethod("newVirtualThreadExecutor");
    }
}
