/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigSource;

/**
 * Utility class that locates the meta configuration source.
 */
final class MetaConfigFinder {
    /**
     * System property used to set a file with meta configuration.
     * This can also be used in combination with {@link #CONFIG_PROFILE_SYSTEM_PROPERTY}
     * to define a custom location of profile specific files.
     */
    public static final String META_CONFIG_SYSTEM_PROPERTY = "io.helidon.config.meta-config";
    /**
     * System property used to set a configuration profile. This profile is then used to discover
     * meta configuration named {@code config-profile-${config.profile}.xxx"}.
     *
     * @see #CONFIG_PROFILE_ENVIRONMENT_VARIABLE
     */
    public static final String CONFIG_PROFILE_SYSTEM_PROPERTY = "config.profile";

    /**
     * System property used to set a configuration profile. This profile is then used to discover
     * meta configuration named {@code config-profile-${config.profile}.xxx"}.
     * This property is Helidon specific (in case the {@link #CONFIG_PROFILE_SYSTEM_PROPERTY} is
     * in use by another component).
     *
     * @see #CONFIG_PROFILE_ENVIRONMENT_VARIABLE
     * @see #CONFIG_PROFILE_SYSTEM_PROPERTY
     */
    public static final String HELIDON_CONFIG_PROFILE_SYSTEM_PROPERTY = "helidon.config.profile";

    /**
     * Environment variable used to set a configuration profile.
     * Environment variable is the most significant.
     *
     * @see #CONFIG_PROFILE_SYSTEM_PROPERTY
     */
    public static final String CONFIG_PROFILE_ENVIRONMENT_VARIABLE = "HELIDON_CONFIG_PROFILE";

    private static final Logger LOGGER = Logger.getLogger(MetaConfigFinder.class.getName());
    private static final List<String> CONFIG_SUFFIXES = List.of("yaml", "conf", "json", "properties");
    private static final String META_CONFIG_PREFIX = "meta-config.";
    private static final String CONFIG_PREFIX = "application.";
    // set of files/classpath resources that were found yet we have no parser configured for them
    // limit logging to once per file
    private static final Set<String> FILES_LOGGED = new HashSet<>();
    private static final Set<String> CLASSPATH_LOGGED = new HashSet<>();

    private MetaConfigFinder() {
    }

    static Optional<Config> findMetaConfig(Function<String, Boolean> supportedMediaType, List<String> supportedSuffixes) {
        return findMetaConfigSource(supportedMediaType, supportedSuffixes)
                .map(source -> Config.builder(source).build());
    }

    static Optional<ConfigSource> findConfigSource(Function<String, Boolean> supportedMediaType, List<String> supportedSuffixes) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return findSource(supportedMediaType, cl, CONFIG_PREFIX, "config source", supportedSuffixes);
    }

    private static Optional<ConfigSource> findMetaConfigSource(Function<String, Boolean> supportedMediaType,
                                                               List<String> supportedSuffixes) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Optional<ConfigSource> source;

        // check if meta configuration is configured using system property
        String metaConfigFile = System.getProperty(META_CONFIG_SYSTEM_PROPERTY);
        // check name of the profile
        String profileName = System.getenv(CONFIG_PROFILE_ENVIRONMENT_VARIABLE);
        if (profileName == null) {
            profileName = System.getProperty(HELIDON_CONFIG_PROFILE_SYSTEM_PROPERTY);
        }
        if (profileName == null) {
            profileName = System.getProperty(CONFIG_PROFILE_SYSTEM_PROPERTY);
        }

        if (metaConfigFile != null && profileName != null) {
            // we have both profile name and meta configuration file defined
            // this means we want to have a custom profile file (maybe a custom location) combined with a profile
            int lastDot = metaConfigFile.lastIndexOf('.');
            String metaWithProfile;
            if (lastDot == 0) {
                // .configuration -> dev.configuration
                metaWithProfile = profileName + metaConfigFile.substring(lastDot);
            } else if (lastDot > 0) {
                // config/profile/profile.yaml -> config/profile/profile-dev.yaml
                metaWithProfile = metaConfigFile.substring(0, lastDot) + "-" + profileName + metaConfigFile.substring(lastDot);
            } else {
                // config/configuration -> config/configuration-dev
                metaWithProfile = metaConfigFile + "-" + profileName;
            }
            source = findFile(metaWithProfile, "config profile");
            if (source.isPresent()) {
                return source;
            }
            source = findClasspath(cl, metaWithProfile, "config profile");
            if (source.isPresent()) {
                return source;
            }
            LOGGER.info("Custom profile file not found: " + metaWithProfile);
        }
        if (metaConfigFile == null) {
            if (profileName != null) {
                return Optional.of(profileSource(supportedMediaType, cl, profileName, supportedSuffixes));
            }
        } else {
            // is it a file
            source = findFile(metaConfigFile, "meta configuration");
            if (source.isPresent()) {
                return source;
            }
            // so it is a classpath resource?
            source = findClasspath(cl, metaConfigFile, "meta configuration");
            if (source.isPresent()) {
                return source;
            }

            LOGGER.info("Meta configuration file not found: " + metaConfigFile);
        }

        return findSource(supportedMediaType, cl, META_CONFIG_PREFIX, "meta configuration", supportedSuffixes)
                .or(() -> findSource(supportedMediaType, cl, "config-profile.", "config profile", supportedSuffixes));
    }

    private static ConfigSource profileSource(Function<String, Boolean> supportedMediaType,
                                              ClassLoader cl,
                                              String profileName,
                                              List<String> supportedSuffixes) {
        // first try to find the profile itself
        // default name is `config-profile.xxx`, we start with `config-profile-${profile}.xxx`
        String profileFileName = "config-profile-" + profileName + ".";
        // first find files for each supported suffix (first one wins)
        for (String supportedSuffix : supportedSuffixes) {
            Optional<ConfigSource> profile = findFile(profileFileName + supportedSuffix, "config profile");
            if (profile.isPresent()) {
                return profile.get();
            }
        }
        // now let's do the same thing with classpath
        for (String supportedSuffix : supportedSuffixes) {
            Optional<ConfigSource> profile = findClasspath(cl, profileFileName + supportedSuffix, "config profile");
            if (profile.isPresent()) {
                return profile.get();
            }
        }
        // we did not find a config profile, let's create one with the usual suspects
        // to make things easier, let's try both application.xxx and META-INF/microprofile-config.properties
        ListNode.Builder sourceListBuilder = ListNode.builder();

        sourceListBuilder.addObject(ObjectNode.builder().addValue("type", "environment-variables").build())
                .addObject(ObjectNode.builder().addValue("type", "system-properties").build());

        // all profile files
        for (String supportedSuffix : supportedSuffixes) {
            addFile(sourceListBuilder, "application-" + profileName, supportedSuffix);
        }

        // all profile classpath
        for (String supportedSuffix : supportedSuffixes) {
            addClasspath(sourceListBuilder, "application-" + profileName, supportedSuffix);
        }

        // all main files
        for (String supportedSuffix : supportedSuffixes) {
            addFile(sourceListBuilder, "application", supportedSuffix);
        }

        // all main classpath
        for (String supportedSuffix : supportedSuffixes) {
            addClasspath(sourceListBuilder, "application", supportedSuffix);
        }

        addClasspath(sourceListBuilder, "META-INF/microprofile-config-" + profileName, "properties");
        addClasspath(sourceListBuilder, "META-INF/microprofile-config", "properties");

        return ConfigSources.create(ObjectNode.builder()
                                            .addList("sources", sourceListBuilder.build())
                                            .build());
    }

    private static void addClasspath(ListNode.Builder sourceListBuilder, String fileName, String supportedSuffix) {
        sourceListBuilder.addObject(ObjectNode.builder()
                                            .addValue("type", "classpath")
                                            .addObject("properties", ObjectNode.builder()
                                                    .addValue("resource", fileName + "." + supportedSuffix)
                                                    .addValue("optional", "true")
                                                    .build())
                                            .build());
    }

    private static void addFile(ListNode.Builder sourceListBuilder, String fileName, String supportedSuffix) {
        sourceListBuilder.addObject(ObjectNode.builder()
                                            .addValue("type", "file")
                                            .addObject("properties", ObjectNode.builder()
                                                    .addValue("path", fileName + "." + supportedSuffix)
                                                    .addValue("optional", "true")
                                                    .build())
                                            .build());
    }

    private static Optional<ConfigSource> findSource(Function<String, Boolean> supportedMediaType,
                                                     ClassLoader cl,
                                                     String configPrefix,
                                                     String type,
                                                     List<String> supportedSuffixes) {
        Optional<ConfigSource> source;

        // we try to find these files, as we expect them to be used, if they are not supported by any
        // parser, we log a warning (probably a misconfiguration)
        Set<String> invalidSuffixes = new HashSet<>(CONFIG_SUFFIXES);
        invalidSuffixes.addAll(supportedSuffixes);

        // these are the ones we are interested in
        Set<String> validSuffixes = new LinkedHashSet<>();
        CONFIG_SUFFIXES.stream()
                .filter(suffix -> supportedMediaType.apply(MediaTypes.detectExtensionType(suffix).orElse("unknown/unknown")))
                .forEach(validSuffixes::add);

        supportedSuffixes.stream()
                .filter(suffix -> supportedMediaType.apply(MediaTypes.detectExtensionType(suffix).orElse("unknown/unknown")))
                .forEach(validSuffixes::add);

        validSuffixes.forEach(invalidSuffixes::remove);

        //  look into the file system - in current user directory
        source = validSuffixes.stream()
                .map(suf -> configPrefix + suf)
                .map(it -> findFile(it, type))
                .flatMap(Optional::stream)
                .findFirst();

        if (source.isPresent()) {
            return source;
        }

        // and finally try to find meta configuration on classpath
        source = validSuffixes.stream()
                .map(suf -> configPrefix + suf)
                .map(resource -> MetaConfigFinder.findClasspath(cl, resource, type))
                .flatMap(Optional::stream)
                .findFirst();

        if (source.isPresent()) {
            return source;
        }

        // now let's see if we have one of the invalid suffixes available
        invalidSuffixes.stream()
                .map(suf -> configPrefix + suf)
                .forEach(it -> {
                    Optional<ConfigSource> found = findFile(it, type);
                    if (found.isPresent()) {
                        if (FILES_LOGGED.add(it)) {
                            LOGGER.warning("Configuration file "
                                                   + it
                                                   + " is on file system, yet there is no parser configured for it");
                        }
                    }
                    found = MetaConfigFinder.findClasspath(cl, it, type);
                    if (found.isPresent()) {
                        if (CLASSPATH_LOGGED.add(it)) {
                            LOGGER.warning("Configuration file "
                                                   + it
                                                   + " is on classpath, yet there is no parser configured for it");
                        }
                    }
                });

        return Optional.empty();
    }

    private static Optional<ConfigSource> findFile(String name, String type) {
        Path path = Paths.get(name);
        if (Files.exists(path) && Files.isReadable(path) && !Files.isDirectory(path)) {
            LOGGER.info("Found " + type + " file: " + path.toAbsolutePath());
            return Optional.of(ConfigSources.file(path).build());
        }
        return Optional.empty();
    }

    private static Optional<ConfigSource> findClasspath(ClassLoader cl, String name, String type) {
        // so it is a classpath resource?
        URL resource = cl.getResource(name);
        if (null != resource) {
            LOGGER.fine(() -> "Found " + type + " resource: " + resource.getPath());
            return Optional.of(ConfigSources.classpath(name).build());
        }
        return Optional.empty();
    }
}
