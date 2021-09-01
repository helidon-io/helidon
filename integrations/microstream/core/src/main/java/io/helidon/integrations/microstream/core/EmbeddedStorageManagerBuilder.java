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

package io.helidon.integrations.microstream.core;

import java.time.Duration;
import java.util.Map;

import io.helidon.config.Config;

import one.microstream.configuration.types.ByteSize;
import one.microstream.storage.embedded.configuration.types.EmbeddedStorageConfigurationBuilder;
import one.microstream.storage.embedded.configuration.types.EmbeddedStorageFoundationCreatorConfigurationBased;
import one.microstream.storage.embedded.types.EmbeddedStorageManager;
import one.microstream.storage.types.StorageEntityCacheEvaluator;

/**
 *
 * Builder for Microstream EmbeddedStorageManager.
 *
 */
public class EmbeddedStorageManagerBuilder implements io.helidon.common.Builder<EmbeddedStorageManager> {
    private final EmbeddedStorageConfigurationBuilder configurationBuilder;

    private EmbeddedStorageManagerBuilder() {
        super();
        configurationBuilder = EmbeddedStorageConfigurationBuilder.New();
    }

    /**
     * A builder for the EmbeddedStorageManager.
     *
     * @return a new fluent API builder
     */
    public static EmbeddedStorageManagerBuilder builder() {
        return new EmbeddedStorageManagerBuilder();
    }

    /**
     *
     * Create a EmbeddedStorageManager instance from Config.
     *
     * @param config configuration to use
     * @return new EmbeddedStorageManager instance
     */
    public static EmbeddedStorageManager create(Config config) {
        return builder().config(config).build();
    }

    @Override
    public EmbeddedStorageManager build() {
        return EmbeddedStorageFoundationCreatorConfigurationBased.New(configurationBuilder.buildConfiguration())
                .createEmbeddedStorageFoundation().createEmbeddedStorageManager();
    }

    /**
     * Update builder from configuration.
     *
     * @param config
     * @return the fluent API builder
     */
    public EmbeddedStorageManagerBuilder config(Config config) {
        Map<String, String> configMap = config.detach().asMap().get();

        configMap.forEach(configurationBuilder::set);

        return this;
    }

    /**
     * The base directory of the storage in the file system.
     *
     * @param storageDirectory location of the storage as string.
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder storageDirectory(String storageDirectory) {
        configurationBuilder.setStorageDirectory(storageDirectory);
        return this;
    }

    /**
     * The backup directory.
     *
     * @param backupDirectory location of the storage backup as string.
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder backupDirectory(String backupDirectory) {
        configurationBuilder.setBackupDirectory(backupDirectory);
        return this;
    }

    /**
     * The deletion directory.
     *
     * @param deletionDirectory location of the storage's deleted files backup as string.
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder deletionDirectory(String deletionDirectory) {
        configurationBuilder.setDeletionDirectory(deletionDirectory);
        return this;
    }

    /**
     * The truncation directory.
     *
     * @param truncationDirectory location of the storage's truncation files backup as string.
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder truncationDirectory(String truncationDirectory) {
        configurationBuilder.setDeletionDirectory(truncationDirectory);
        return this;
    }

    /**
     * Name prefix of the subdirectories used by the channel threads. Default is
     * <code>"channel_"</code>.
     *
     * @param channelDirectoryPrefix new prefix
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder channelDirectoryPrefix(String channelDirectoryPrefix) {
        configurationBuilder.setChannelDirectoryPrefix(channelDirectoryPrefix);
        return this;
    }

    /**
     * Name prefix of the storage files. Default is <code>"channel_"</code>.
     *
     * @param dataFilePrefix new prefix
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder dataFilePrefix(String dataFilePrefix) {
        configurationBuilder.setDataFilePrefix(dataFilePrefix);
        return this;
    }

    /**
     * Name suffix of the storage files. Default is <code>".dat"</code>.
     *
     * @param dataFileSuffix new suffix
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder dataFileSuffix(String dataFileSuffix) {
        configurationBuilder.setDataFileSuffix(dataFileSuffix);
        return this;
    }

    /**
     * Name prefix of the storage transaction file. Default is
     * <code>"transactions_"</code>.
     *
     * @param transactionFilePrefix new prefix
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder transactionFilePrefix(String transactionFilePrefix) {
        configurationBuilder.setTransactionFilePrefix(transactionFilePrefix);
        return this;
    }

    /**
     * Name suffix of the storage transaction file. Default is <code>".sft"</code>.
     *
     * @param transactionFileSuffix new suffix
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder transactionFileSuffix(String transactionFileSuffix) {
        configurationBuilder.setTransactionFileSuffix(transactionFileSuffix);
        return this;
    }

    /**
     * The name of the dictionary file. Default is
     * <code>"PersistenceTypeDictionary.ptd"</code>.
     *
     * @param typeDictionaryFilename new name
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder typeDictionaryFilename(String typeDictionaryFilename) {
        configurationBuilder.setTypeDictionaryFileName(typeDictionaryFilename);
        return this;
    }

    /**
     * Name suffix of the storage rescue files. Default is ".bak".
     *
     * @param rescuedFileSuffix
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder rescuedFileSuffix(String rescuedFileSuffix) {
        configurationBuilder.setRescuedFileSuffix(rescuedFileSuffix);
        return this;
    }

    /**
     * Name of the lock file. Default is "used.lock"
     *
     * @param lockFileName
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder lockFileName(String lockFileName) {
        configurationBuilder.setLockFileName(lockFileName);
        return this;
    }

    /**
     * The number of threads and number of directories used by the storage engine.
     * Every thread has exclusive access to its directory. Default is
     * <code>1</code>.
     *
     * @param channelCount the new channel count, must be a power of 2
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder channelCount(int channelCount) {
        configurationBuilder.setChannelCount(channelCount);
        return this;
    }

    /**
     * Interval in milliseconds for the houskeeping. This is work like garbage
     * collection or cache checking. In combination with
     * {@link #housekeepingTimeBudget(Duration)} the maximum processor time for
     * housekeeping work can be set. Default is <code>1000</code> (every second).
     *
     * @param housekeepingInterval the new interval
     * @return EmbeddedStorageManager builder
     * @see #housekeepingTimeBudget(Duration)
     */
    public EmbeddedStorageManagerBuilder housekeepingInterval(Duration housekeepingInterval) {
        configurationBuilder.setHousekeepingInterval(housekeepingInterval);
        return this;
    }

    /**
     * Number of nanoseconds used for each housekeeping cycle. However, no matter
     * how low the number is, one item of work will always be completed. But if
     * there is nothing to clean up, no processor time will be wasted. Default is
     * <code>10000000</code> (10 million nanoseconds = 10 milliseconds = 0.01
     * seconds).
     *
     * @param housekeepingTimeBudget the new time budget
     * @return EmbeddedStorageManager builder
     * @see #housekeepingInterval(Duration)
     */
    public EmbeddedStorageManagerBuilder housekeepingTimeBudget(Duration housekeepingTimeBudget) {
        configurationBuilder.setHousekeepingTimeBudget(housekeepingTimeBudget);
        return this;
    }

    /**
     * Timeout in milliseconds for the entity cache evaluator. If an entity wasn't
     * accessed in this timespan it will be removed from the cache. Default is
     * <code>86400000</code> (1 day).
     *
     * @param entityCacheTimeout
     * @return EmbeddedStorageManager builder
     * @see Duration
     */
    public EmbeddedStorageManagerBuilder entityCacheTimeout(Duration entityCacheTimeout) {
        configurationBuilder.setEntityCacheTimeout(entityCacheTimeout);
        return this;
    }

    /**
     * Abstract threshold value for the lifetime of entities in the cache. See
     * {@link StorageEntityCacheEvaluator}. Default is <code>1000000000</code>.
     *
     * @param entityCacheThreshold the new threshold
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder entityCacheThreshold(long entityCacheThreshold) {
        configurationBuilder.setEntityCacheThreshold(entityCacheThreshold);
        return this;
    }

    /**
     * Minimum file size for a data file to avoid cleaning it up. Default is 1024^2
     * = 1 MiB.
     *
     * @param dataFileMinimumSize the new minimum file size
     * @return EmbeddedStorageManager builder
     * @see #dataFileMinimumUseRatio(double)
     */
    public EmbeddedStorageManagerBuilder dataFileMinimumSize(ByteSize dataFileMinimumSize) {
        configurationBuilder.setDataFileMinimumSize(dataFileMinimumSize);
        return this;
    }

    /**
     * Maximum file size for a data file to avoid cleaning it up. Default is
     * 1024^2*8 = 8 MiB.
     *
     * @param dataFileMaximumSize the new maximum file size
     * @return EmbeddedStorageManager builder
     * @see #dataFileMinimumUseRatio(double)
     */
    public EmbeddedStorageManagerBuilder dataFileMaximumSize(ByteSize dataFileMaximumSize) {
        configurationBuilder.setDataFileMaximumSize(dataFileMaximumSize);
        return this;
    }

    /**
     * The ratio (value in ]0.0;1.0]) of non-gap data contained in a storage file to
     * prevent the file from being dissolved. "Gap" data is anything that is not the
     * latest version of an entity's data, including older versions of an entity and
     * "comment" bytes (a sequence of bytes beginning with its length as a negative
     * value length header).<br>
     * The closer this value is to 1.0 (100%), the less disk space is occupied by
     * storage files, but the more file dissolving (data transfers to new files) is
     * required and vice versa.
     *
     * @param dataFileMinimumUseRatio the new minimum use ratio
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder dataFileMinimumUseRatio(double dataFileMinimumUseRatio) {
        configurationBuilder.setDataFileMinimumUseRatio(dataFileMinimumUseRatio);
        return this;
    }

    /**
     * A flag defining whether the current head file (the only file actively written
     * to) shall be subjected to file cleanups as well.
     *
     * @param dataFileCleanupHeadFile
     * @return EmbeddedStorageManager builder
     */
    public EmbeddedStorageManagerBuilder dataFileCleanupHeadFile(boolean dataFileCleanupHeadFile) {
        configurationBuilder.setDataFileCleanupHeadFile(dataFileCleanupHeadFile);
        return this;
    }

}
