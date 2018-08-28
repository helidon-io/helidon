/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.internal;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.config.ConfigException;

/**
 * Utilities for file-related source classes.
 *
 * @see FileConfigSource
 * @see FileOverrideSource
 * @see DirectoryConfigSource
 */
public class FileSourceHelper {

    private static final Logger LOGGER = Logger.getLogger(FileSourceHelper.class.getName());

    private FileSourceHelper() {
        throw new AssertionError("Instantiation not allowed.");
    }

    /**
     * Returns the last modified time of the given file or directory.
     *
     * @param path a file or directory
     * @return the last modified time
     */
    public static Instant lastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path.toRealPath()).toInstant();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, e, () -> "Cannot obtain the last modified time of '" + path + "'.");
        }
        Instant timestamp = Instant.MAX;
        LOGGER.finer("Cannot obtain the last modified time. Used time '" + timestamp + "' as a content timestamp.");
        return timestamp;
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
            } catch (NonWritableChannelException e) {
                // non writable channel means that we do not need to lock it
            }
            try {
                try (BufferedReader bufferedReader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    return bufferedReader.lines().collect(Collectors.joining("\n"));
                } catch (IOException e) {
                    throw new ConfigException(String.format("Cannot read from path '%s'", path));
                }
            } finally {
                if (lock != null) {
                    lock.release();
                }
            }
        } catch (FileNotFoundException e) {
            throw new ConfigException(String.format("File '%s' not found.", path), e);
        } catch (IOException e) {
            throw new ConfigException(String.format("Cannot obtain a lock for file '%s'.", path), e);
        }
    }

    /**
     * Returns an MD5 digest of the specified file or null if the file cannot be read.
     *
     * @param path a path to the file
     * @return an MD5 digest of the file or null if the file cannot be read
     */
    public static byte[] digest(Path path) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new ConfigException("Cannot get MD5 algorithm.", e);
        }
        try {
            try (InputStream is = Files.newInputStream(path);
                    DigestInputStream dis = new DigestInputStream(is, md)) {
                byte[] buffer = new byte[4096];
                while (dis.read(buffer) != -1) {
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINEST, "Cannot get a digest.", e);
            return null;
        }
        return md.digest();
    }
}
