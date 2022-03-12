/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.config.hocon;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.Builder;
import io.helidon.config.FileConfigSource;
import io.helidon.config.PrimordialConfig;
import io.helidon.config.spi.ConfigParser;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigIncludeContext;
import com.typesafe.config.ConfigIncluder;
import com.typesafe.config.ConfigIncluderClasspath;
import com.typesafe.config.ConfigIncluderFile;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;
import io.helidon.config.spi.ConfigParserException;

/**
 * HOCON ConfigParser Builder.
 * <p>
 * {@link com.typesafe.config.Config#resolve() HOCON resolving substitutions support} is by default enabled.
 * {@link ConfigResolveOptions#defaults()} is used to resolve loaded configuration.
 * It is possible to {@link #disableResolving() disable resolving} feature
 * or specify custom {@link #resolveOptions(ConfigResolveOptions) ConfigResolveOptions} instance.
 */
public final class HoconConfigParserBuilder implements Builder<HoconConfigParserBuilder, ConfigParser> {

    private static final Logger LOGGER = Logger.getLogger(HoconConfigParser.class.getName());

    public static final String HELIDON_CONFIG_INCLUDES_ALLOW = "helidon.config.includes.allow";

    private boolean resolvingEnabled;
    private ConfigResolveOptions resolveOptions;

    // lazily computed ...
    private Boolean allowIncludes;
    private Path basePath;
    // lazily computed ... we defer the creation of parseOptions since we infer it from the resolve options if left null
    private ConfigParseOptions parseOptions;

    HoconConfigParserBuilder() {
        resolvingEnabled = true;
        resolveOptions = ConfigResolveOptions.defaults();
        allowIncludes = false;
    }

    /**
     * Disables HOCON resolving substitutions support.
     *
     * @return modified builder instance
     */
    public HoconConfigParserBuilder disableResolving() {
        resolvingEnabled = false;
        return this;
    }

    /**
     * Sets custom instance of {@link ConfigResolveOptions}.
     * <p>
     * By default {@link ConfigResolveOptions#defaults()} is used.
     *
     * @param resolveOptions resolve options
     *
     * @return modified builder instance
     */
    public HoconConfigParserBuilder resolveOptions(ConfigResolveOptions resolveOptions) {
        this.resolveOptions = Objects.requireNonNull(resolveOptions);
        return this;
    }

    /**
     * Applicable iff a custom {@link ConfigParseOptions} is NOT being used. In this case will control the default
     * behavior of HOCON allowing includes, which is normally turned off.
     *
     * @return modified builder instance
     *
     * @see #defaultAllowIncludes()
     */
    public HoconConfigParserBuilder allowIncludes() {
        return allowIncludes(true);
    }

    /**
     * Applicable iff a custom {@link ConfigParseOptions} is NOT being used. In this case will control the default
     * behavior of HOCON allowing includes, which is normally turned off.
     *
     * @return modified builder instance
     */
    public HoconConfigParserBuilder allowIncludes(boolean allowIncludes) {
        this.allowIncludes = allowIncludes;
        return this;
    }

    /**
     * Sets custom instance of {@link ConfigParseOptions}.
     * <p>
     * By default {@link #defaultParseOptions(ConfigResolveOptions, Path, boolean)} is used.
     *
     * @param parseOptions parse options (required)
     * @return modified builder instance
     */
    public HoconConfigParserBuilder parseOptions(ConfigParseOptions parseOptions) {
        this.parseOptions = Objects.requireNonNull(parseOptions);
        return this;
    }

    /**
     * Sets custom base config path.
     *
     * @param basePath base config path (required)
     * @return modified builder instance
     */
    public HoconConfigParserBuilder baseConfigPath(Path basePath) {
        this.basePath = Objects.requireNonNull(basePath);
        return this;
    }

    /**
     * Builds new instance of HOCON ConfigParser.
     *
     * @return new instance of HOCON ConfigParser.
     */
    @Override
    public HoconConfigParser build() {
        Path baseConfigPath = Objects.nonNull(this.basePath) ?
            this.basePath : defaultBasePath();
        boolean allowIncludes = Objects.nonNull(this.allowIncludes)
            ? this.allowIncludes : defaultAllowIncludes();
        ConfigParseOptions parseOptions = Objects.nonNull(this.parseOptions) ?
            this.parseOptions : defaultParseOptions(resolveOptions, baseConfigPath, allowIncludes);
        return new HoconConfigParser(resolvingEnabled, resolveOptions, parseOptions);
    }

    /**
     * The context root directory for all config
     */
    public static Path defaultBasePath() {
        return FileConfigSource.defaultBasePath();
    }

    /**
     * Hocon's #include is disabled by default. It can be enabled by settings this sys/env property:
     * {@link #HELIDON_CONFIG_INCLUDES_ALLOW}
     */
    public static boolean defaultAllowIncludes() {
        // TODO: discuss with Tomas
        return PrimordialConfig.getProp(HELIDON_CONFIG_INCLUDES_ALLOW, false);
    }

    /**
     * Creates {@link ConfigParseOptions} that having a contextual base config file path. Will build
     * a strategy that prefers file path first and fallback to a classpath based strategy. Further, the implementation
     * will optionally provide a means to activate hocon's include behavior which is turned off by default.
     *
     * @param resolveOptions    (required) resolve options
     * @param basePath          (required) base config path
     * @param allowIncludes     should includes be allowed by hocon
     *
     * @return config parse options configured "properly"
     */
    public static ConfigParseOptions defaultParseOptions(ConfigResolveOptions resolveOptions,
                                                         Path basePath,
                                                         boolean allowIncludes) {
        Objects.requireNonNull(resolveOptions, "resolveOptions parameter is mandatory");

        ConfigParseOptions parseOptions = ConfigParseOptions.defaults();

        // if a file is referenced it should throw an exception if not found
        if (Objects.nonNull(resolveOptions)) {
            parseOptions = parseOptions.setAllowMissing(resolveOptions.getAllowUnresolved());
        }

        // create an includer based upon the callers preferences
        parseOptions = parseOptions.setIncluder(new PathBasedConfigIncluder(parseOptions, basePath, allowIncludes));

        return parseOptions;
    }


    static class PathBasedConfigIncluder implements ConfigIncluder, ConfigIncluderClasspath, ConfigIncluderFile {
        private final ConfigParseOptions parseOptions;
        private final File basePath;
        private final boolean allowIncludes;

        public PathBasedConfigIncluder(ConfigParseOptions parseOptions, Path basePath, boolean allowIncludes) {
            this.parseOptions = parseOptions;
            this.basePath = basePath.toFile();
            this.allowIncludes = allowIncludes;
        }

        @Override
        public ConfigIncluder withFallback(ConfigIncluder fallback) {
            /*
             * Just return this since this includer doesn't support chaining.  As
             * per the TypeSafe Config ConfigIncluder.withFallback documentation.
             */
            return this;
        }

        @Override
        public ConfigObject include(ConfigIncludeContext context, String what) {
            LOGGER.log(Level.FINER, String.format("Received request to include resource %s, %s",
                what, context.parseOptions().getOriginDescription()));

            if (!allowIncludes) {
                throw includesNotAllowedException();
            }

            File configFile = new File(basePath, what);
            ConfigObject cfg = tryIncludeFile(context, configFile);
            if (null == cfg) {
                cfg = includeResources(context, what);
            }
            return cfg;
        }

        private ConfigParserException includesNotAllowedException() {
            return new ConfigParserException(
                // note that this really means to set sys prop or env prop right now - TODO: review with Tomas
                String.format("includes are disabled by default - to enable includes set %s=true",
                    HELIDON_CONFIG_INCLUDES_ALLOW));
        }

        @Override
        public ConfigObject includeResources(ConfigIncludeContext context, String what) {
            LOGGER.log(Level.FINER, "including resource {0}", what);
            return ConfigFactory.parseResourcesAnySyntax(what, parseOptions).root();
        }

        @Override
        public ConfigObject includeFile(ConfigIncludeContext context, File file) {
            return ConfigFactory.parseFileAnySyntax(file, parseOptions).root();
        }

        /**
         * Try to load a config from a file. If the config file isn't found return null.
         *
         * @param context current TypeSafe context
         * @param file The file attempted to be loaded
         *
         * @return ConfigObject parsed from file or null if no file exists.
         */
        private ConfigObject tryIncludeFile(ConfigIncludeContext context, File file) {
            LOGGER.log(Level.FINER, "Attempting to include file {0}", file.getAbsolutePath());
            try {
                return includeFile(context, file);
            } catch (ConfigException.IO e) {
                Throwable temp = e.getCause();
                /*
                 * TypeSafeConfig wraps all exceptions with a ConfigException. If an exception is
                 * thrown from multiple layers of config there will be a ConfigException wrapper for
                 * each level.
                 *
                 * In the event that the file is not found, which is what we want to check for,
                 * look through the wrapped causes and see if the first exception after removing the
                 * ConfigException wrappers is a FileNotFoundException return null. Otherwise, throw
                 * the exception.
                 */
                while (temp != null
                    && temp.getClass().isAssignableFrom(ConfigException.IO.class)
                    && temp != temp.getCause()) {
                    temp = temp.getCause();
                }
                if (temp instanceof FileNotFoundException) {
                    // we'll check the classpath if not found on the filesystem
                    return null;
                }
                throw e;
            }
        }
    }

}
