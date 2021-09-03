/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.config.git;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Objects;

import io.helidon.common.Builder;
import io.helidon.config.AbstractConfigSourceBuilder;
import io.helidon.config.Config;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ParsableSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

/**
 * Git ConfigSource builder.
 * <p>
 * Creates a {@link GitConfigSource} while allowing the application to the following properties:
 * <ul>
 * <li>{@code path} - a relative path to the configuration file in repository</li>
 * <li>{@code uri} - an uri to the repository</li>
 * <li>{@code directory} - a directory with a cloned repository - by default it is a temporary dir created by calling {@link
 * Files#createTempFile(String, String, FileAttribute[])} with the prefix {@code helidon-config-git-source-}</li>
 * <li>{@code branch} - a git branch - a default value is {@code master}</li>
 * <li>{@code optional} - is existence of configuration resource mandatory (by default) or is {@code optional}?</li>
 * <li>{@code media-type} - configuration content media type to be used to look for appropriate {@link ConfigParser};</li>
 * <li>{@code parser} - or directly set {@link ConfigParser} instance to be used to parse the source;</li>
 * </ul>
 * <p>
 * The application should invoke the builder's {@code build} method in a
 * {@code try-release} block -- or otherwise make sure that it invokes the
 * {@code GitConfigSource#close} method -- to clean up the config source when the
 * application no longer needs it.
 * <p>
 * If the directory is not set, a temporary directory is created (see {@link Files#createTempDirectory(String, FileAttribute[])}.
 * The temporary file is cleaned up by the {@link GitConfigSource#close()} method).
 * A specified directory, that is not empty, must be a valid git repository, otherwise an exception is thrown.
 * If the directory nor the uri is not set, an exception is thrown.
 * <p>
 * One of {@code media-type} and {@code parser} properties must be set to be clear how to parse the content. If both of them
 * are set, then {@code parser} has precedence.
 */
public final class GitConfigSourceBuilder
        extends AbstractConfigSourceBuilder<GitConfigSourceBuilder, GitConfigSourceBuilder.GitEndpoint>
        implements Builder<GitConfigSource>,
                   PollableSource.Builder<GitConfigSourceBuilder>,
                   ParsableSource.Builder<GitConfigSourceBuilder> {


    private static final String PATH_KEY = "path";
    private static final String URI_KEY = "uri";
    private static final String BRANCH_KEY = "branch";
    private static final String DIRECTORY_KEY = "directory";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private String path;
    private URI uri;
    private String branch = "master";
    private Path directory;
    private CredentialsProvider credentialsProvider;

    GitConfigSourceBuilder() {
        this.credentialsProvider = CredentialsProvider.getDefault();
    }

    /**
     * Configure path to use.
     *
     * @param path path to the configuration file
     * @return updated builder instance
     */
    public GitConfigSourceBuilder path(String path) {
        this.path = path;
        return this;
    }


    @Override
    public GitConfigSource build() {
        if (null == path) {
            throw new IllegalArgumentException("git path must be defined");
        }

        return new GitConfigSource(this, target());
    }

    /**
     * {@inheritDoc}
     * <ul>
     * <li>{@code path} - type {@code String}, see {@link #path(String)}</li>
     * <li>{@code uri} - type {@code URI}, see {@link #uri(URI)}</li>
     * <li>{@code branch} - type {@code String}, see {@link #branch(String)}</li>
     * <li>{@code directory} - type {@code Path}, see {@link #directory(Path)}</li>
     * </ul>
     *
     * @param metaConfig configuration properties used to initialize a builder instance.
     * @return modified builder instance
     */
    @Override
    public GitConfigSourceBuilder config(Config metaConfig) {
        metaConfig.get(PATH_KEY).asString().ifPresent(this::path);
        //uri
        metaConfig.get(URI_KEY).as(URI.class)
                .ifPresent(this::uri);
        //branch
        metaConfig.get(BRANCH_KEY).asString()
                .ifPresent(this::branch);
        //directory
        metaConfig.get(DIRECTORY_KEY).as(Path.class)
                .ifPresent(this::directory);

        metaConfig.get(USERNAME).as(String.class)
                .ifPresent(user -> {
                    String password = metaConfig.get(PASSWORD).as(String.class).orElse(null);
                    this.credentialsProvider = new UsernamePasswordCredentialsProvider(user, password);
                });

        return super.config(metaConfig);
    }

    @Override
    public GitConfigSourceBuilder parser(ConfigParser parser) {
        return super.parser(parser);
    }

    @Override
    public GitConfigSourceBuilder mediaType(String mediaType) {
        return super.mediaType(mediaType);
    }

    @Override
    public GitConfigSourceBuilder pollingStrategy(PollingStrategy pollingStrategy) {
        return super.pollingStrategy(pollingStrategy);
    }

    GitEndpoint target() {
        return new GitEndpoint(uri, branch, directory, path, credentialsProvider);
    }

    /**
     * Sets a git branch to checkout.
     *
     * @param branch a git branch
     * @return this builder
     */
    public GitConfigSourceBuilder branch(String branch) {
        this.branch = branch;
        return this;
    }

    /**
     * Sets a directory where the repository is cloned or should be cloned.
     *
     * @param directory a local git repository
     * @return this builder
     */
    public GitConfigSourceBuilder directory(Path directory) {
        this.directory = directory;
        return this;
    }

    /**
     * Sets an uri to the repository.
     *
     * @param uri an uri to the repository
     * @return this builder
     */
    public GitConfigSourceBuilder uri(URI uri) {
        this.uri = uri;
        return this;
    }

    /**
     * Sets user and password to the repository.
     *
     * @param user user to the repository
     * @param password password to the repository
     * @return this builder
     */
    public GitConfigSourceBuilder credentials(String user, String password) {
        Objects.requireNonNull(user);
        this.credentialsProvider = new UsernamePasswordCredentialsProvider(user, password);
        return this;
    }

    /**
     * Sets new {@link CredentialsProvider} which should be used by application.
     *
     * @param credentialsProvider credentials provider
     * @return this builder
     */
    public GitConfigSourceBuilder credentialsProvider(CredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
        return this;
    }

    /**
     * Git source endpoint descriptor.
     * <p>
     * Holds attributes necessary to get a configuration from a remote Git repository.
     */
    public static class GitEndpoint {

        private final URI uri;
        private final String branch;
        private final Path directory;
        private final String path;
        private final CredentialsProvider credentialsProvider;

        /**
         * Creates a descriptor.
         * @param uri       a remote git repository uri
         * @param branch    a git branch
         * @param directory a local git directory
         * @param path      a relative path to the configuration file
         * @param credentialsProvider a credentials provider
         */
        public GitEndpoint(URI uri,
                           String branch,
                           Path directory,
                           String path,
                           CredentialsProvider credentialsProvider) {
            this.uri = uri;
            this.branch = branch;
            this.path = path;
            this.directory = directory;
            this.credentialsProvider = credentialsProvider;
        }

        /**
         * Returns a remote git repository uri.
         *
         * @return a remote git repository uri
         */
        public URI uri() {
            return uri;
        }

        /**
         * Returns a git branch.
         *
         * @return a git branch
         */
        public String branch() {
            return branch;
        }

        /**
         * Returns a local git directory.
         *
         * @return a local git directory
         */
        public Path directory() {
            return directory;
        }

        /**
         * Returns a relative path to the configuration file.
         *
         * @return a relative path to the configuration file
         */
        public String path() {
            return path;
        }

        /**
         * Returns an instance of {@link CredentialsProvider}.
         *
         * @return credentials provider instance
         */
        public CredentialsProvider credentialsProvider() {
            return credentialsProvider;
        }
    }
}
