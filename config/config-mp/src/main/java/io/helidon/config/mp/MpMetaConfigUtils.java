/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.config.mp;

import java.lang.System.Logger.Level;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.helidon.config.ConfigException;
import io.helidon.config.ConfigValue;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Utilities for Helidon MicroProfile Meta-Config implementation.
 */
public class MpMetaConfigUtils {
    private static final System.Logger LOGGER = System.getLogger(MpMetaConfig.class.getName());

    private MpMetaConfigUtils() {
    }

    /**
     * A utility for providing a list of configuration sources.
     *
     * @param config configuration properties from a meta-config type
     * @param profile name of the profile to use or null if not used
     * @param fromPath Function used to process a config specified by a filepath
     * @param fromClasspath Function used to process a config specified by a classpath
     * @param fromClasspathWithProfile BiFunction used to process a config specified by a classpath and a profile name
     * @param fromUrl Function used to process a config specified by a Url
     *
     * @return list of configuration sources
     */
    public static List<ConfigSource> sourceFromMeta(io.helidon.config.Config config,
                                              String profile,
                                              Function<Path, ConfigSource> fromPath,
                                              Function<String, List<ConfigSource>> fromClasspath,
                                              BiFunction<String, String, List<ConfigSource>> fromClasspathWithProfile,
                                              Function<URL, ConfigSource> fromUrl) {

        boolean optional = config.get("optional").asBoolean().orElse(false);

        String location;
        Exception cause = null;

        ConfigValue<Path> pathConfig = config.get("path").as(Path.class);
        if (pathConfig.isPresent()) {
            Path path = pathConfig.get();
            List<ConfigSource> result = sourceFromPathMeta(path, profile, fromPath);

            if (!result.isEmpty()) {
                return result;
            }
            // else the file was not found, check optional
            location = "path " + path.toAbsolutePath();
        } else {
            ConfigValue<String> classpathConfig = config.get("classpath").as(String.class);
            if (classpathConfig.isPresent()) {
                String classpath = classpathConfig.get();
                List<ConfigSource> sources;

                if (profile == null) {
                    sources = fromClasspath.apply(classpath);
                } else {
                    sources = fromClasspathWithProfile.apply(classpath, profile);
                }

                if (!sources.isEmpty()) {
                    return sources;
                }
                location = "classpath " + classpath;
            } else {
                ConfigValue<URL> urlConfig = config.get("url").as(URL.class);
                if (urlConfig.isPresent()) {
                    URL url = urlConfig.get();
                    List<ConfigSource> sources = null;
                    try {
                        sources = sourceFromUrlMeta(url, profile, fromUrl);
                    } catch (ConfigException e) {
                        cause = e;
                    }

                    if (sources != null && !sources.isEmpty()) {
                        return sources;
                    }
                    location = "url " + url;
                } else {
                    throw new ConfigException("MP meta configuration does not contain config source location. Node: " + config
                            .key());
                }
            }
        }

        if (optional) {
            return List.of();
        }
        String message = "Meta configuration could not find non-optional config source on " + location;
        if (cause == null) {
            throw new ConfigException(message);
        } else {
            throw new ConfigException(message, cause);
        }
    }

    private static List<ConfigSource> sourceFromUrlMeta(URL url, String profile, Function<URL, ConfigSource> fromUrl) {
        ConfigSource profileSource = null;
        ConfigSource mainSource = null;
        Exception cause = null;

        if (profile != null) {
            try {
                String profileUrl = toProfileName(url.toString(), profile);
                profileSource = fromUrl.apply(new URL(profileUrl));
            } catch (Exception e) {
                cause = e;
            }
        }

        try {
            mainSource = fromUrl.apply(url);
            if (cause != null) {
                LOGGER.log(Level.TRACE, "Failed to load profile URL resource, succeeded loading main from " + url, cause);
            }
        } catch (ConfigException e) {
            if (cause != null) {
                e.addSuppressed(cause);
                throw e;
            } else {
                if (profileSource == null) {
                    throw e;
                } else {
                    LOGGER.log(Level.TRACE, "Did not find main URL config source from " + url + ", have profile source", e);
                }
            }
        }
        return composite(mainSource, profileSource);
    }

    private static List<ConfigSource> sourceFromPathMeta(Path path, String profile, Function<Path, ConfigSource> fromPath) {
        ConfigSource profileSource = null;
        ConfigSource mainSource = null;

        if (profile != null) {
            Path fileNamePath = path.getFileName();
            String fileName = (fileNamePath == null ? "" : fileNamePath.toString());
            fileName = toProfileName(fileName, profile);
            Path profileSpecific = path.resolveSibling(fileName);
            if (Files.exists(profileSpecific) && Files.isRegularFile(profileSpecific)) {
                profileSource = fromPath.apply(profileSpecific);
            }
        }

        if (Files.exists(path) && Files.isRegularFile(path)) {
            mainSource = fromPath.apply(path);
        }

        // now handle profile
        return composite(mainSource, profileSource);
    }

    private static List<ConfigSource> composite(ConfigSource mainSource, ConfigSource profileSource) {
        // now handle profile
        if (profileSource == null) {
            if (mainSource == null) {
                return List.of();
            }
            return List.of(mainSource);
        }
        if (mainSource == null) {
            return List.of(profileSource);
        }

        return List.of(MpConfigSources.composite(profileSource, mainSource));
    }

    private static String toProfileName(String fileName, String profile) {
        int i = fileName.lastIndexOf('.');
        if (i > -1) {
            return fileName.substring(0, i) + "-" + profile + fileName.substring(i);
        }
        return fileName + "-" + profile;
    }


}
