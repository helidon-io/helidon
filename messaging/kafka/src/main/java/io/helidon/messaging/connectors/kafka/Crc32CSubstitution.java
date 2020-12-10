/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.nio.ByteBuffer;
import java.util.zip.Checksum;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.apache.kafka.common.utils.Checksums;


/**
 * Method handles are not supported by native-image,
 * invoke {@link java.util.zip.CRC32C CRC32C} directly.
 *
 * Helidon runs only on Java 11 and newer, {@link java.util.zip.CRC32C CRC32C}
 * doesn't have to be instantiated by method handles.
 */
@TargetClass(org.apache.kafka.common.utils.Crc32C.class)
@Substitute
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
final class Crc32CSubstitution {

    @Substitute
    public static long compute(byte[] bytes, int offset, int size) {
        Checksum crc = create();
        crc.update(bytes, offset, size);
        return crc.getValue();
    }

    @Substitute
    public static long compute(ByteBuffer buffer, int offset, int size) {
        Checksum crc = create();
        Checksums.update(crc, buffer, offset, size);
        return crc.getValue();
    }

    @Substitute
    public static Checksum create() {
        return new java.util.zip.CRC32C();
    }
}
