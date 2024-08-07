/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.protobuf.Message;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;

final class ProtoMarshaller {
    private static final Map<Class<?>, MethodDescriptor.Marshaller<?>> CACHE = new ConcurrentHashMap<>();

    private ProtoMarshaller() {
    }

    @SuppressWarnings("unchecked")
    static <T> MethodDescriptor.Marshaller<T> get(Class<T> clazz) {
        MethodDescriptor.Marshaller<T> result = (MethodDescriptor.Marshaller<T>) CACHE.get(clazz);
        if (result != null) {
            return result;
        }
        // it may create it twice, but that should not really matter
        try {
            java.lang.reflect.Method getDefaultInstance = clazz.getDeclaredMethod("getDefaultInstance");
            Message instance = (Message) getDefaultInstance.invoke(clazz);

            result = (MethodDescriptor.Marshaller<T>) ProtoUtils.marshaller(instance);
            MethodDescriptor.Marshaller<T> current = (MethodDescriptor.Marshaller<T>) CACHE.putIfAbsent(clazz, result);
            return current == null ? result : current;
        } catch (ReflectiveOperationException e) {
            String msg = "Attempting to use class \""
                    + clazz.getName()
                    + "\", which is not a valid Protocol buffer message, with a default marshaller";

            throw new IllegalArgumentException(msg, e);
        }
    }
}
