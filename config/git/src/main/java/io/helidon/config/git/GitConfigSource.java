/*
 * Copyright (c) 2017, 2026 Oracle and/or its affiliates.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.AbstractConfigSource;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.FileSourceHelper;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParser.Content;
import io.helidon.config.spi.ParsableSource;
import io.helidon.config.spi.PollableSource;
import io.helidon.config.spi.PollingStrategy;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import static java.util.Collections.singleton;

/**
 * A config source which loads a configuration document from Git repository.
 * <p>
 * Config source is initialized by {@link GitConfigSourceBuilder}.
 */
public class GitConfigSource extends AbstractConfigSource
        implements ParsableSource, PollableSource<byte[]>, AutoCloseable {

    private static final System.Logger LOGGER = System.getLogger(GitConfigSource.class.getName());

    private final URI uri;
    private final String branch;

    private Path directory;
    private Path repositoryRoot;
    private Path targetPath;
    private Repository repository;

    private GitConfigSourceBuilder.GitEndpoint endpoint;

    private boolean isTempDirectory = false;
    private boolean isClosed = false;
    private final List<Git> gits = Collections.synchronizedList(new ArrayList<>());

    /**
     * Initializes config source from builder.
     *
     * @param builder builder to be initialized from
     */
    GitConfigSource(GitConfigSourceBuilder builder, GitConfigSourceBuilder.GitEndpoint endpoint) {
        super(builder);

        this.endpoint = endpoint;
        this.uri = endpoint.uri();
        this.branch = endpoint.branch();
        if (endpoint.directory() == null) {
            if (uri == null) {
                throw new ConfigException("Directory or Uri must be set.");
            }
            try {
                this.directory = Files.createTempDirectory("helidon-config-git-source-");
                isTempDirectory = true;
            } catch (IOException e) {
                throw new ConfigException("Cannot create temporary directory.", e);
            }
        } else {
            this.directory = endpoint.directory();
        }
    }

    @Override
    public void init(ConfigContext context) {
        Objects.requireNonNull(context, "context");
        try {
            init();
            repositoryRoot = directory.toRealPath();
            targetPath = resolveInRepository(repositoryRoot, endpoint.path());
        } catch (IOException | GitAPIException | JGitInternalException e) {
            String repository = uri == null ? directory.toString() : uri.toASCIIString();
            throw new ConfigException(String.format("Cannot initialize repository '%s' in local temp dir %s",
                                                    repository,
                                                    directory.toString()),
                                      e);
        }
    }

    /**
     * Create an instance from meta configuration.
     *
     * @param metaConfig meta configuration of this source
     * @return config source configured from the meta configuration
     */
    public static GitConfigSource create(Config metaConfig) {
        return builder().config(metaConfig).build();
    }

    /**
     * Create a fluent API builder for GIT config source.
     *
     * @return a new builder instance
     */
    public static GitConfigSourceBuilder builder() {
        return new GitConfigSourceBuilder();
    }

    @Override
    protected String uid() {
        StringBuilder sb = new StringBuilder();
        if (endpoint.directory() != null) {
            sb.append(endpoint.directory());
        }
        if (endpoint.uri() != null && endpoint.directory() != null) {
            sb.append('|');
        }
        if (endpoint.uri() != null) {
            sb.append(endpoint.uri().toASCIIString());
        }
        sb.append('#');
        sb.append(endpoint.path());
        return sb.toString();
    }

    @Override
    public Optional<ConfigParser> parser() {
        return super.parser();
    }

    @Override
    public Optional<PollingStrategy> pollingStrategy() {
        return super.pollingStrategy();
    }

    @Override
    public boolean isModified(byte[] stamp) {
        try {
            pull();
        } catch (GitAPIException e) {
            LOGGER.log(Level.WARNING, "Pull failed.", e);
        }
        return existingRepositoryPath(targetPath, endpoint.path())
                .map(path -> FileSourceHelper.isModified(path, stamp))
                .orElse(true);
    }

    @Override
    public Optional<Content> load() throws ConfigException {
        Optional<Path> existingPath = existingRepositoryPath(targetPath, endpoint.path());
        if (existingPath.isEmpty()) {
            return Optional.empty();
        }

        return FileSourceHelper.readDataAndDigest(existingPath.get())
                .map(dad -> Content.builder()
                        .data(new ByteArrayInputStream(dad.data()))
                        .stamp(dad.digest())
                        .mediaType(MediaTypes.detectType(targetPath))
                        .build());
    }

    @Override
    public Function<String, Optional<InputStream>> relativeResolver() {
        return it -> {
            Path path = resolveInRepository(targetPath.getParent(), it);
            Optional<Path> existingPath = existingRepositoryPath(path, it);
            if (existingPath.isPresent()
                    && Files.isReadable(existingPath.get())
                    && !Files.isDirectory(existingPath.get())) {
                try {
                    return Optional.of(Files.newInputStream(existingPath.get()));
                } catch (IOException e) {
                    throw new ConfigException("Failed to read configuration from path: " + existingPath.get(), e);
                }
            } else {
                return Optional.empty();
            }
        };
    }

    @Override
    public Optional<MediaType> mediaType() {
        return super.mediaType();
    }

    private Path resolveInRepository(Path base, String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            throw new ConfigException("Git configuration path must be relative: " + configuredPath);
        }

        Path resolved = base.resolve(path).normalize();
        if (!resolved.startsWith(repositoryRoot)) {
            throw new ConfigException("Git configuration path must stay inside the repository: " + configuredPath);
        }
        return resolved;
    }

    private Optional<Path> existingRepositoryPath(Path path, String configuredPath) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        Path realPath;
        try {
            realPath = path.toRealPath();
        } catch (IOException e) {
            throw new ConfigException("Failed to resolve git configuration path: " + configuredPath, e);
        }

        if (!realPath.startsWith(repositoryRoot)) {
            throw new ConfigException("Git configuration path must stay inside the repository: " + configuredPath);
        }
        return Optional.of(realPath);
    }

    private void init() throws IOException, GitAPIException {

        if (!Files.exists(directory)) {
            throw new ConfigException(String.format("Directory '%s' does not exist.", directory.toString()));
        }

        if (!Files.isDirectory(directory)) {
            throw new ConfigException(String.format("'%s' is not a directory.", directory.toString()));
        }

        if (!Files.isReadable(directory) || !Files.isWritable(directory)) {
            throw new ConfigException(String.format("Directory '%s' is not accessible.", directory.toString()));
        }

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
            if (dirStream.iterator().hasNext()) {
                try {
                    recordGit(Git.open(directory.toFile()));
                } catch (IOException e) {
                    throw new ConfigException(
                            String.format("Directory '%s' is not empty and it is not a valid repository.", directory.toString()));
                }
            } else if (uri != null) {
                CloneCommand cloneCommand = Git.cloneRepository()
                        .setCredentialsProvider(endpoint.credentialsProvider())
                        .setURI(uri.toASCIIString())
                        .setBranchesToClone(singleton("refs/heads/" + branch))
                        .setBranch("refs/heads/" + branch)
                        .setDirectory(directory.toFile());
                Git cloneResult = recordGit(cloneCommand.call());
                LOGGER.log(Level.DEBUG, () -> String.format("git clone result: %s", cloneResult.toString()));
            }
        }

        repository = new FileRepositoryBuilder()
                .setGitDir(directory.resolve(".git").toFile())
                .build();

        // make sure we have the latest data before we start using this config source
        pull();
    }

    private void pull() throws GitAPIException {
        Git git = recordGit(Git.wrap(repository));
        PullCommand pull = git.pull()
                .setCredentialsProvider(endpoint.credentialsProvider())
                .setRebase(true);
        PullResult result = pull.call();

        if (!result.isSuccessful()) {
            LOGGER.log(Level.WARNING, () -> String.format("Cannot pull from git '%s', branch '%s'", uri.toASCIIString(), branch));

            if (LOGGER.isLoggable(Level.TRACE)) {
                Status status = git.status().call();
                LOGGER.log(Level.TRACE, () -> "git status cleanliness: " + status.isClean());
                if (!status.isClean()) {
                    LOGGER.log(Level.TRACE, () -> "git status uncommitted changes: " + status.getUncommittedChanges());
                    LOGGER.log(Level.TRACE, () -> "git status untracked: " + status.getUntracked());
                }
            }
        } else {
            LOGGER.log(Level.DEBUG, "Pull was successful.");
        }
        LOGGER.log(Level.TRACE, () -> "git rebase result: " + result.getRebaseResult().getStatus().name());
        LOGGER.log(Level.TRACE, () -> "git fetch result: " + result.getFetchResult().getMessages());
    }

    GitConfigSourceBuilder.GitEndpoint gitEndpoint() {
        return endpoint;
    }

    private Git recordGit(Git git) {
        gits.add(git);
        return git;
    }

    @Override
    public void close() throws IOException {
        if (!isClosed) {
            try {
                if (repository != null) {
                    repository.close();
                }
                closeGits();
                if (isTempDirectory) {
                    deleteTempDirectory();
                }
            } finally {
                isClosed = true;
            }
        }
    }

    private void closeGits() {
        gits.forEach(Git::close);
    }

    private void deleteTempDirectory() throws IOException {
        LOGGER.log(Level.DEBUG, () -> String.format("GitConfigSource deleting temp directory %s", directory.toString()));
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!Files.isWritable(file)) {
                    //When you try to delete the file on Windows and it is marked as read-only
                    //it would fail unless this change
                    file.toFile().setWritable(true);
                }

                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
