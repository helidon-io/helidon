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
 */

package io.helidon.config.sources;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import io.helidon.config.ConfigException;

public class FileSourceHelper {
    public static Optional<byte[]> fileStamp(Path filePath) {
        MessageDigest digest = digest();

        try (InputStream fis = Files.newInputStream(filePath)) {
            DigestInputStream dis = new DigestInputStream(fis, digest);
            byte[] buffer = new byte[4096];
            while (dis.read(buffer) != -1) {
                // just discard - we are only interested in the digest information
            }
            return Optional.of(digest.digest());
        } catch (FileNotFoundException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new ConfigException("Failed to calculate digest for file: " + filePath, e);
        }
    }
    public static Optional<DataAndDigest> readDataAndDigest(Path filePath) {
        // lock the file, so somebody does not remove it or update it while we process it
        ByteArrayOutputStream baos = createByteArrayOutput(filePath);
        MessageDigest md = digest();

        // we want to digest and read at the same time
        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            FileLock lock = lockFile(filePath, fis);

            try {
                DigestInputStream dis = new DigestInputStream(fis, md);
                byte[] buffer = new byte[4096];
                int len;
                while ((len = dis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
            } finally {
                if (lock != null) {
                    lock.release();
                }
            }
        } catch (FileNotFoundException e) {
            // race condition - the file disappeared between call to exists and load
            return Optional.empty();
        } catch (IOException e) {
            throw new ConfigException(String.format("Cannot handle file '%s'.", filePath), e);
        }

        return Optional.of(new DataAndDigest(baos.toByteArray(), md.digest()));
    }

    private static FileLock lockFile(Path filePath, FileInputStream fis) throws IOException {
        try {
            FileLock lock = fis.getChannel().tryLock(0L, Long.MAX_VALUE, false);
            if (null == lock) {
                throw new ConfigException("Failed to acquire a lock on configuration file " + filePath + ", cannot safely "
                                                  + "read it");
            }
            return lock;
        } catch (NonWritableChannelException e) {
            // non writable channel means that we do not need to lock it
            return null;
        }
    }

    private static ByteArrayOutputStream createByteArrayOutput(Path filePath) {
        try {
            return new ByteArrayOutputStream((int) Files.size(filePath));
        } catch (IOException e) {
            return new ByteArrayOutputStream(4096);
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new ConfigException("Cannot get MD5 digest algorithm.", e);
        }
    }

    public static class DataAndDigest {
        private byte[] data;
        private byte[] digest;

        private DataAndDigest(byte[] data, byte[] digest) {
            this.data = data;
            this.digest = digest;
        }

        public byte[] data() {
            return data;
        }

        public byte[] digest() {
            return digest;
        }
    }
}
