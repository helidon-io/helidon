/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.messaging.connectors.kafka;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import io.helidon.common.LazyValue;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.utils.BufferSupplier;
import org.apache.kafka.common.utils.ByteBufferInputStream;
import org.apache.kafka.common.utils.ByteBufferOutputStream;

/**
 * Helper for creating ZSTD or SNAPPY compression stream wrappers without method handles.
 */
@SuppressWarnings("checkstyle:OuterTypeFilename")
final class CompressionTypeHelper {

    private CompressionTypeHelper() {
    }

    private static boolean zstdNativeLibLoaded = false;

    static final LazyValue<Constructor<?>> LAZY_INPUT_ZSTD =
            LazyValue.create(() -> findConstructor("com.github.luben.zstd.ZstdInputStream", InputStream.class));
    static final LazyValue<Constructor<?>> LAZY_OUTPUT_ZSTD =
            LazyValue.create(() -> findConstructor("com.github.luben.zstd.ZstdOutputStream", OutputStream.class));
    static final LazyValue<Constructor<?>> LAZY_INPUT_SNAPPY =
            LazyValue.create(() -> findConstructor("org.xerial.snappy.SnappyInputStream", InputStream.class));
    static final LazyValue<Constructor<?>> LAZY_OUTPUT_SNAPPY =
            LazyValue.create(() -> findConstructor("org.xerial.snappy.SnappyOutputStream", OutputStream.class));

    static OutputStream snappyOutputStream(OutputStream orig) {
        try {
            return (OutputStream) LAZY_OUTPUT_SNAPPY.get().newInstance(orig);
        } catch (KafkaException e) {
            throw e;
        } catch (Exception e) {
            throw new KafkaException(e);
        }
    }

    static InputStream snappyInputStream(ByteBuffer orig) {
        try {
            return (InputStream) LAZY_INPUT_SNAPPY.get().newInstance(new ByteBufferInputStream(orig));
        } catch (KafkaException e) {
            throw e;
        } catch (Exception e) {
            throw new KafkaException(e);
        }
    }

    static void zstdLoadNativeLibs() throws ReflectiveOperationException {
        // loading jni libs in static blocks is not supported
        // see https://github.com/oracle/graal/issues/439#issuecomment-394341725
        if (!zstdNativeLibLoaded) {
            Class<?> clazz = Class.forName("com.github.luben.zstd.util.Native");
            Field loadedField = clazz.getDeclaredField("loaded");
            loadedField.setAccessible(true);
            loadedField.setBoolean(null, false);
            Method loadMethod = clazz.getDeclaredMethod("load");
            loadMethod.invoke(null);
            zstdNativeLibLoaded = true;
        }
    }

    static OutputStream zstdOutputStream(OutputStream orig) {
        try {
            zstdLoadNativeLibs();
            return (OutputStream) LAZY_OUTPUT_ZSTD.get().newInstance(orig);
        } catch (KafkaException e) {
            throw e;
        } catch (Exception e) {
            throw new KafkaException(e);
        }
    }

    static InputStream zstdInputStream(ByteBuffer orig) {
        try {
            zstdLoadNativeLibs();
            return (InputStream) LAZY_INPUT_ZSTD.get().newInstance(new ByteBufferInputStream(orig));
        } catch (KafkaException e) {
            throw e;
        } catch (Exception e) {
            throw new KafkaException(e);
        }
    }

    static Constructor<?> findConstructor(String className, Class<?>... paramTypes) {
        try {
            return Class.forName(className)
                    .getDeclaredConstructor(paramTypes);
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            throw new KafkaException(e);
        }
    }
}

/**
 * Substitution for {@link org.apache.kafka.common.record.CompressionType#SNAPPY CompressionType.SNAPPY}.
 */
@TargetClass(className = "org.apache.kafka.common.record.CompressionType$3")
@SuppressWarnings("checkstyle:OneTopLevelClass")
final class SnappySubstitution {

    @Substitute
    public OutputStream wrapForOutput(ByteBufferOutputStream buffer, byte messageVersion) {
        return CompressionTypeHelper.snappyOutputStream(buffer);
    }

    @Substitute
    public InputStream wrapForInput(ByteBuffer buffer, byte messageVersion, BufferSupplier decompressionBufferSupplier) {
        return CompressionTypeHelper.snappyInputStream(buffer);
    }
}

/**
 * Substitution for {@link org.apache.kafka.common.record.CompressionType#ZSTD CompressionType.ZSTD}.
 */
@TargetClass(className = "org.apache.kafka.common.record.CompressionType$5")
@SuppressWarnings("checkstyle:OneTopLevelClass")
final class ZstdSubstitution {

    @Substitute
    public OutputStream wrapForOutput(ByteBufferOutputStream buffer, byte messageVersion) {
        return CompressionTypeHelper.zstdOutputStream(buffer);
    }

    @Substitute
    public InputStream wrapForInput(ByteBuffer buffer, byte messageVersion, BufferSupplier decompressionBufferSupplier) {
        return CompressionTypeHelper.zstdInputStream(buffer);
    }
}
