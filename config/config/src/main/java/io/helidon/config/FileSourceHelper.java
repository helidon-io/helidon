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

package io.helidon.config;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Utilities for file-related source classes.
 *
 * @see io.helidon.config.FileConfigSource
 * @see FileOverrideSource
 * @see io.helidon.config.DirectoryConfigSource
 */
public final class FileSourceHelper {

    private static final Logger LOGGER = Logger.getLogger(FileSourceHelper.class.getName());
    private static final int FILE_BUFFER_SIZE = 4096;

    private FileSourceHelper() {
        throw new AssertionError("Instantiation not allowed.");
    }

    /**
     * Returns the last modified time of the given file or directory.
     *
     * @param path a file or directory
     * @return the last modified time
     */
    public static Optional<Instant> lastModifiedTime(Path path) {
        try {
            return Optional.of(Files.getLastModifiedTime(path.toRealPath()).toInstant());
        } catch (FileNotFoundException e) {
            return Optional.empty();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, e, () -> "Cannot obtain the last modified time of '" + path + "'.");
        }
        Instant timestamp = Instant.MIN;
        LOGGER.finer("Cannot obtain the last modified time. Used time '" + timestamp + "' as a content timestamp.");
        return Optional.of(timestamp);
    }

    /**
     * Reads the content of the specified file.
     * <p>
     * The file is locked before the reading and the lock is released immediately after the reading.
     * <p>
     * An expected encoding is UTF-8.
     *
     * @param path a path to the file
     * @return a content of the file
     */
    public static String safeReadContent(Path path) {
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            FileLock lock = null;
            try {
                lock = fis.getChannel().tryLock(0L, Long.MAX_VALUE, false);
            } catch (NonWritableChannelException ignored) {
                // non writable channel means that we do not need to lock it
            }
            try {
                try (BufferedReader bufferedReader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    return bufferedReader.lines().collect(Collectors.joining("\n"));
                } catch (IOException e) {
                    throw new ConfigException(String.format("Cannot read from path '%s'", path), e);
                }
            } finally {
                if (lock != null) {
                    lock.release();
                }
            }
        } catch (FileNotFoundException e) {
            throw new ConfigException(String.format("File '%s' not found. Absolute path: '%s'", path, path.toAbsolutePath()), e);
        } catch (IOException e) {
            throw new ConfigException(String.format("Cannot obtain a lock for file '%s'. Absolute path: '%s'",
                                                    path,
                                                    path.toAbsolutePath()), e);
        }
    }

    /**
     * Returns an MD5 digest of the specified file or null if the file cannot be read.
     * <p>
     * The file is locked before the reading and the lock is released immediately after the reading.
     *
     * @param path a path to the file
     * @return an MD5 digest of the file or null if the file cannot be read
     */
    @SuppressWarnings("StatementWithEmptyBody")
    public static Optional<byte[]> digest(Path path) {
        MessageDigest digest = digest();

        try (DigestInputStream dis = new DigestInputStream(Files.newInputStream(path), digest)) {
            byte[] buffer = new byte[FILE_BUFFER_SIZE];
            while (dis.read(buffer) != -1) {
                // just discard - we are only interested in the digest information
            }
            return Optional.of(digest.digest());
        } catch (FileNotFoundException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new ConfigException("Failed to calculate digest for file: " + path, e);
        }
    }

    /**
     * Check if a file on the file system is changed, as compared to the digest provided.
     *
     * @param filePath path of the file
     * @param digest digest of the file
     * @return {@code true} if the file exists and has the same digest, {@code false} otherwise
     */
    public static boolean isModified(Path filePath, byte[] digest) {
        return !digest(filePath)
                .map(newDigest -> Arrays.equals(digest, newDigest))
                // if new stamp is not present, it means the file was deleted
                .orElse(false);
    }

    /**
     * Check if a file on the file system is changed based on its last modification timestamp.
     *
     * @param filePath path of the file
     * @param stamp last modification stamp
     * @return {@code true} if the file exists and has the same last modification timestamp, {@code false} otherwise
     */
    public static boolean isModified(Path filePath, Instant stamp) {
        return lastModifiedTime(filePath)
                .map(newStamp -> newStamp.isAfter(stamp))
                .orElse(false);
    }

    /**
     * Read data and its digest in the same go.
     *
     * @param filePath path to load data from
     * @return data and its digest, or empty if file does not exist
     */
    public static Optional<DataAndDigest> readDataAndDigest(Path filePath) {
        // lock the file, so somebody does not remove it or update it while we process it
        ByteArrayOutputStream baos = createByteArrayOutput(filePath);
        MessageDigest md = digest();

        // we want to digest and read at the same time
        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            FileLock lock = lockFile(filePath, fis);

            try (DigestInputStream dis = new DigestInputStream(fis, md)) {
                byte[] buffer = new byte[FILE_BUFFER_SIZE];
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

    private static ByteArrayOutputStream createByteArrayOutput(Path filePath) {
        try {
            return new ByteArrayOutputStream((int) Files.size(filePath));
        } catch (IOException e) {
            return new ByteArrayOutputStream(4096);
        }
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

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new ConfigException("Cannot get MD5 digest algorithm.", e);
        }
    }

    /**
     * Data and digest of a file.
     * Data in an instance are guaranteed to be paired - e.g. the digest is for the bytes provided.
     */
    public static final class DataAndDigest {
        private final byte[] data;
        private final byte[] digest;

        private DataAndDigest(byte[] data, byte[] digest) {
            this.data = data;
            this.digest = digest;
        }

        /**
         * Data loaded from the file.
         * @return bytes of the file
         */
        public byte[] data() {
            byte[] result = new byte[data.length];
            System.arraycopy(data, 0, result, 0, data.length);
            return result;
        }

        /**
         * Digest of the data that was loaded.
         * @return bytes of the digest
         */
        public byte[] digest() {
            byte[] result = new byte[digest.length];
            System.arraycopy(digest, 0, result, 0, digest.length);
            return result;
        }
    }
}
