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

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.internal.FileSourceHelper;
import io.helidon.config.spi.AbstractParsableConfigSource;
import io.helidon.config.spi.ConfigParser;

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
public class GitConfigSource extends AbstractParsableConfigSource<byte[]> {

    private static final Logger LOGGER = Logger.getLogger(GitConfigSource.class.getName());

    static {
        HelidonFeatures.register("Config", "git");
    }

    private final URI uri;
    private final String branch;

    private Path directory;
    private Path targetPath;
    private Repository repository;

    private GitConfigSourceBuilder.GitEndpoint endpoint;

    private boolean isTempDirectory = false;
    private boolean isClosed = false;
    private final List<Git> gits = Collections.synchronizedList(new ArrayList<>());

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

    /**
     * Create a fluent API builder for GIT config source for a file.
     *
     * @param path path of the configuration file
     * @return a new builder instance
     * @deprecated use {@link #builder(String)} instead
     */
    @Deprecated
    public static GitConfigSourceBuilder builder(String path) {
        return new GitConfigSourceBuilder().path(path);
    }

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

        try {
            init();
            targetPath = directory.resolve(endpoint.path());
        } catch (IOException | GitAPIException | JGitInternalException e) {
            throw new ConfigException(String.format("Cannot initialize repository '%s' in local temp dir %s",
                                                    uri.toASCIIString(),
                                                    directory.toString()),
                                      e);
        }

    }

    private void init() throws IOException, GitAPIException {

        if (!directory.toFile().exists()) {
            throw new ConfigException(String.format("Directory '%s' does not exist.", directory.toString()));
        }

        if (!directory.toFile().isDirectory()) {
            throw new ConfigException(String.format("'%s' is not a directory.", directory.toString()));
        }

        if (!directory.toFile().canRead() || !directory.toFile().canWrite()) {
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
                LOGGER.log(Level.CONFIG, () -> String.format("git clone result: %s", cloneResult.toString()));
            }
        }

        repository = new FileRepositoryBuilder()
                .setGitDir(directory.resolve(".git").toFile())
                .build();
    }

    private void pull() throws GitAPIException {
        Git git = recordGit(Git.wrap(repository));
        PullCommand pull = git.pull()
                .setCredentialsProvider(endpoint.credentialsProvider())
                .setRebase(true);
        PullResult result = pull.call();

        if (!result.isSuccessful()) {
            LOGGER.log(Level.WARNING, () -> String.format("Cannot pull from git '%s', branch '%s'", uri.toASCIIString(), branch));

            if (LOGGER.isLoggable(Level.FINEST)) {
                Status status = git.status().call();
                LOGGER.finest(() -> "git status cleanliness: " + status.isClean());
                if (!status.isClean()) {
                    LOGGER.finest(() -> "git status uncommitted changes: " + status.getUncommittedChanges());
                    LOGGER.finest(() -> "git status untracked: " + status.getUntracked());
                }
            }
        } else {
            LOGGER.fine("Pull was successful.");
        }
        LOGGER.finest(() -> "git rebase result: " + result.getRebaseResult().getStatus().name());
        LOGGER.finest(() -> "git fetch result: " + result.getFetchResult().getMessages());
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
    protected String mediaType() {
        return Optional.ofNullable(super.mediaType())
                .or(this::probeContentType)
                .orElse(null);
    }

    private Optional<String> probeContentType() {
        return MediaTypes.detectType(targetPath);
    }

    @Override
    protected Optional<byte[]> dataStamp() {
        try {
            pull();
        } catch (GitAPIException e) {
            LOGGER.log(Level.WARNING, "Pull failed.", e);
        }
        return Optional.ofNullable(FileSourceHelper.digest(targetPath));
    }

    private Instant lastModifiedTime(Path path) {
        return FileSourceHelper.lastModifiedTime(path);
    }

    @Override
    protected ConfigParser.Content<byte[]> content() throws ConfigException {
        Instant lastModifiedTime = lastModifiedTime(targetPath);
        LOGGER.log(Level.FINE, String.format("Getting content from '%s'. Last stamp is %s.", targetPath, lastModifiedTime));

        LOGGER.finest(FileSourceHelper.safeReadContent(targetPath));
        Optional<byte[]> stamp = dataStamp();
        return ConfigParser.Content.create(new StringReader(FileSourceHelper.safeReadContent(targetPath)),
                                           mediaType(),
                                           stamp);
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
        LOGGER.log(Level.FINE, () -> String.format("GitConfigSource deleting temp directory %s", directory.toString()));
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
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
