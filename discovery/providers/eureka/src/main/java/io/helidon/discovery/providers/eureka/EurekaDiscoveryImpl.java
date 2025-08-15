/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.discovery.providers.eureka;

import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import io.helidon.common.config.NamedService;
import io.helidon.discovery.DiscoveredUri;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientConfig;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_JSON;
import static io.helidon.http.HeaderNames.ACCEPT_ENCODING;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;
import static java.lang.System.getLogger;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSequencedSet;
import static java.util.HashMap.newHashMap;
import static java.util.Objects.requireNonNull;

/**
 * A {@link EurekaDiscovery} implementation.
 */
final class EurekaDiscoveryImpl implements EurekaDiscovery, NamedService {


    /*
     * Static fields.
     */


    /**
     * The constant value ({@value #TYPE}) returned by the {@link #type()} method.
     *
     * @see #type()
     */
    static final String TYPE = "eureka";

    /**
     * An unmodifiable {@linkplain SequencedSet#isEmpty() empty} {@link SequencedSet}.
     */
    private static final SequencedSet<?> EMPTY_SEQUENCED_SET = unmodifiableSequencedSet(new LinkedHashSet<>(0));

    /**
     * The {@link Logger} used by instances of this class.
     *
     * <p>The {@linkplain Logger#getName() name} of the {@link Logger} is {@code
     * io.helidon.discovery.providers.eureka.EurekaDiscoveryImpl}.</p>
     */
    private static final Logger LOGGER = getLogger(EurekaDiscoveryImpl.class.getName());


    /*
     * Instance fields.
     */


    /*
     * final instance fields.
     */

    /**
     * The {@link Http1Client} used to communicate with a Eureka server.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>This field is safe for concurrent use by multiple threads.</p>
     *
     * @see #client()
     */
    private final Http1Client client;

    /**
     * An {@link AtomicReference} holding a {@link Thread} representing the <dfn>fetch thread</dfn>.
     *
     * <p>The fetch thread is a {@link Thread} that periodically retrieves the Eureka service registry, or changes to
     * it, from a Eureka server.</p>
     *
     * <p>The value of this field is relevant only when {@link EurekaDiscoveryConfig#cache()} returns {@code true}.</p>
     *
     * @see #createFetchThread()
     */
    private final AtomicReference<Thread> fetchThread;

    /**
     * A {@link Function} accepting a discovery name and returning an immutable {@link SequencedSet} of {@link
     * Instance}s.
     *
     * <p>This field is used indirectly by the implementation of the {@link #uris(String, URI)} method.</p>
     *
     * @see #uris(String, URI)
     */
    private final Function<? super String, ? extends SequencedSet<Instance>> instancesFunction;

    /**
     * A {@link ReadWriteLock} guarding the {@link #cache} and {@link #stamp} fields.
     *
     * <p>This field is never {@code null}.</p>
     *
     * <p>The value of this field is relevant only when {@link EurekaDiscoveryConfig#cache()} returns {@code true}.</p>
     */
    private final ReadWriteLock lock;

    /**
     * The <dfn>prototype</dfn> for this {@link EurekaDiscoveryImpl}.
     *
     * <p>This field is never {@code null}.</p>
     *
     * @see #prototype()
     *
     * @see EurekaDiscoveryConfig
     */
    private final EurekaDiscoveryConfig prototype;

    /*
     * Non-final instance fields.
     */

    /**
     * Whether this {@link EurekaDiscoveryImpl} has been closed.
     *
     * @see #close()
     *
     * @see #start()
     */
    private volatile boolean closed;

    /**
     * Whether the full Eureka service registry should be fetched.
     *
     * <p>This is {@code true} to start, and then may change later due to configuration, or errors, or both.</p>
     *
     * <p>This field is read and written only by the {@linkplain #fetchThread fetch thread}.</p>
     *
     * <p>The value of this field is relevant only when {@link EurekaDiscoveryConfig#cache()} returns {@code true}.</p>
     *
     * @see EurekaDiscoveryConfig#cache()
     *
     * @see EurekaDiscoveryConfig#registryFetchChanges()
     */
    private boolean fetchAll; // non-volatile on purpose; after construction, read/written only by fetchThread

    /**
     * An immutable {@link Map} of immutable {@link SequencedSet}s of {@link Instance}s, indexed by Eureka
     * <dfn>application name</dfn>.
     *
     * <p>This field must be read and written only while the appropriate read or write lock is held. See {@link
     * #lock}.</p>
     *
     * <p>The value of this field is relevant only when {@link EurekaDiscoveryConfig#cache()} returns {@code true}.</p>
     *
     * @see #lock
     *
     * @see EurekaDiscoveryConfig#cache()
     */
    // @GuardedBy("lock")
    private Map<String, SequencedSet<Instance>> cache;

    /**
     * A <dfn>stamp</dfn> from the most-recently-received, Eureka-supplied "applications" JSON payload.
     *
     * <p>In Eureka version 2.0.5, the {@code apps__hashcode} string that accompanies an "applications" payload contains
     * an opaque value that seems to identify a collection of applications (collections of instances). This stamp seems
     * to be used to identify duplicate information, particularly where Eureka's so-called <dfn>deltas</dfn> are
     * concerned (changesets that apply to a given collection of applications at a particular moment in time). The value
     * and its usage are undocumented, but appear to be critical to the Eureka "deltas" process.</p>
     *
     * <p>This field is initially {@code null} by design and accessed only from the fetch thread.</p>
     *
     * <p>The value of this field is relevant only when {@link EurekaDiscoveryConfig#cache()} returns {@code true}.</p>
     *
     * @see #createFetchThread()
     *
     * @see #change(Object, JsonArray)
     *
     * @see EurekaDiscoveryConfig#cache()
     */
    // @GuardedBy("lock")
    private Object stamp;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link EurekaDiscoveryImpl} from the supplied prototype.
     *
     * @param prototype a {@link EurekaDiscoveryConfig} representing this {@link EurekaDiscoveryImpl}'s state; must not
     * be {@code null}
     *
     * @exception NullPointerException if {@code prototype} is {@code null}
     *
     * @see EurekaDiscoveryConfig
     */
    EurekaDiscoveryImpl(EurekaDiscoveryConfig prototype) {
        super();
        this.lock = new ReentrantReadWriteLock();
        this.client = prototype.client().orElseGet(Http1Client::create);
        this.prototype = prototype;
        this.fetchThread = new AtomicReference<>();
        this.cache = Map.of();
        this.fetchAll = true; // always true for at least the first fetch, then modulated by prototype.registryFetchChanges()
        if (prototype.cache()) {
            this.instancesFunction = this::instancesFromCache;
            Thread fetchThread = this.createFetchThread(); // unstarted
            if (prototype.registryFetchThreadStartEagerly()) {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "Starting registry fetch thread (" + fetchThread.getName() + ")");
                }
                fetchThread.start(); // runs replaceLoop() in normal situations
            }
            this.fetchThread.set(fetchThread);
        } else {
            this.instancesFunction = this::instancesFromServer;
        }
    }


    /*
     * Instance methods.
     */


    /**
     * Closes this {@link EurekaDiscoveryImpl}.
     *
     * <p>A {@link EurekaDiscoveryImpl} may be reused after this method is called. The first call to {@link
     * #uris(String, URI)} may be slower than subsequent calls in this case.</p>
     *
     * @see EurekaDiscovery#close()
     *
     * @see io.helidon.discovery.Discovery#close()
     *
     * @see #uris(String, URI)
     */
    @Override // Discovery
    public void close() {
        boolean closed = this.closed; // volatile read
        if (!closed) {
            // Shut the door.
            this.closed = true; // volatile write

            // Tell the fetch thread to stop.
            Thread fetchThread = this.fetchThread.getAndSet(null);
            if (fetchThread != null) {
              fetchThread.interrupt();
            }

            if (this.prototype().cache()) {
                // Purge the cache.
                this.lock.writeLock().lock();
                try {
                    this.cache = Map.of();
                } finally {
                    this.lock.writeLock().unlock();
                }
            }
        }
    }

    /**
     * Returns {@code true} if the supplied {@link Object} is a {@link EurekaDiscoveryImpl} and has a {@linkplain
     * #prototype() prototype} {@linkplain EurekaDiscoveryConfig#equals(Object) equal to} this {@link
     * EurekaDiscoveryConfig}'s {@linkplain #prototype() prototype}.
     *
     * @param other an {@link Object}; may be {@code null} in which case {@code false} will be returned
     *
     * @return {@code true} if the supplied {@link Object} is equal to this {@link EurekaDiscoveryImpl}
     *
     * @see #prototype()
     *
     * @see EurekaDiscoveryConfig#equals(Object)
     */
    @Override // Object
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else if (other != null && other.getClass() == this.getClass()) {
            return this.prototype().equals(((EurekaDiscoveryImpl) other).prototype());
        } else {
            return false;
        }
    }

    /**
     * Returns the result of invoking the {@link EurekaDiscoveryConfig#hashCode()} method on this {@link
     * EurekaDiscoveryConfig}'s {@linkplain #prototype() prototype}.
     *
     * @return a hashcode for this {@link EurekaDiscoveryImpl}
     */
    @Override // Object
    public int hashCode() {
        return this.prototype().hashCode();
    }

    /**
     * Invokes the {@link EurekaDiscoveryConfig#name()} method on this {@link EurekaDiscoveryImpl}'s {@linkplain
     * #prototype() prototype} and returns the result.
     *
     * @return the result of invoking the {@link EurekaDiscoveryConfig#name()} method on this {@link
     * EurekaDiscoveryImpl}'s {@linkplain #prototype() prototype}
     */
    @Override // EurekaDiscovery (NamedService)
    public String name() {
        return this.prototype().name();
    }

    /**
     * Returns this {@link EurekaDiscoveryImpl}'s {@linkplain EurekaDiscoveryConfig prototype}.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>This method returns a determinate value.</p>
     *
     * @return a non-{@code null} {@link EurekaDiscoveryConfig}
     *
     * @see EurekaDiscovery#prototype()
     *
     * @see EurekaDiscoveryConfig
     */
    @Override // EurekaDiscovery
    public EurekaDiscoveryConfig prototype() {
        return this.prototype;
    }

    /**
     * Returns a non-{@code null} {@link String} representation of this {@link EurekaDiscoveryImpl}.
     *
     * <p>The format of the {@link String} that is returned is subject to change in future versions of this class
     * without prior notice.</p>
     *
     * @return a non-{@code null} {@link String}
     */
    @Override // Object
    public String toString() {
        return this.prototype().toString();
    }

    /**
     * Returns {@value #TYPE} when invoked.
     *
     * @return {@value #TYPE} when invoked
     */
    @Override // NmaedService
    public String type() {
        return TYPE;
    }

    @Override // EurekaDiscovery (Discovery)
    public SequencedSet<DiscoveredUri> uris(String discoveryName, URI defaultValue) {
        return
            this.uris(discoveryName,
                      // A UriFactory (nested class; see below) that imposes an HTTPS or HTTP scheme and default port
                      // numbers based on whether Eureka reports the instance as being "secure" or not.
                      //
                      // Yes, this implies that all host-and-port information managed by a Eureka server is really
                      // suitable for HTTP usage only. Spring, the biggest user of Eureka, forces this by default for
                      // all of its service discovery machinery by default:
                      // https://github.com/spring-cloud/spring-cloud-commons/blob/v4.3.0/spring-cloud-commons/src/main/java/org/springframework/cloud/client/DefaultServiceInstance.java#L87
                      // This assumption may also be part of Eureka itself.
                      //
                      // In future revisions of this class, UriFactory may become public to allow the end user to choose
                      // how to interpret the Eureka information. For now, by Helidon team agreement, it remains
                      // private.
                      (host, port, secure, ignored) -> Optional.of(URI.create((secure ? "https" : "http") // scheme
                                                                              + "://" + host + ":" // host
                                                                              + (port < 0 ? (secure ? 443 : 80) : port))), // port
                      defaultValue);
    }

    /*
     * Private instance methods.
     */

    /**
     * Returns {@code true} if and only if the supplied {@code newStamp} is equal to the {@linkplain #stamp existing
     * stamp}.
     *
     * <p>A stamp is normally the value of the {@code apps__hashcode} field in a Eureka-supplied JSON payload of
     * applications. It has no semantics.</p>
     *
     * <p>This method is called only on the {@linkplain #fetchThread fetch thread}.</p>
     *
     * @param newStamp the stamp to test; must not be {@code null}
     *
     * @return {@code true} if and only if the supplied {@code newStamp} is equal to the {@linkplain #stamp existing
     * stamp}
     *
     * @exception NullPointerException if {@code newStamp} is {@code null}
     */
    // Called only on fetchThread.
    private boolean applied(Object newStamp) {
        requireNonNull(newStamp, "newStamp");
        Object oldStamp;
        this.lock.readLock().lock();
        try {
            oldStamp = this.stamp;
        } finally {
            this.lock.readLock().unlock();
        }
        return newStamp.equals(oldStamp);
    }

    /**
     * Applies <dfn>changes</dfn> represented by the Eureka-supplied {@link JsonArray} and a Eureka-supplied
     * <dfn>stamp</dfn> that accompanies them.
     *
     * <p>The stamp is the value of a special field named {@code apps__hashcode} in a JSON payload that Eureka supplies,
     * indicating, roughly, the set of applications (and their instances) to which the supplied changes apply.</p>
     *
     * <p>This method is called only on the {@linkplain #fetchThread fetch thread}.</p>
     *
     * <p>This method is <em>not</em> idempotent.</p>
     *
     * @param newStamp a stamp; must not be {@code null}
     *
     * @param changes a {@link JsonArray} representing {@code application} entries in a Eureka JSON payload; must not be
     * {@code null}
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #change(Map, JsonArray)
     */
    // Called only from #replaceLoop().
    // Called only on fetchThread.
    private void change(Object newStamp, JsonArray changes) {
        requireNonNull(newStamp, "newStamp");
        Map<String, SequencedSet<Instance>> cache;

        // Read the cache instance field. You don't *really* need a read lock to do this (this.cache is fully
        // immutable), but we want to make sure that we read the "right one", given that writes will also update
        // this.stamp. Acquiring the lock also makes it clearer that we are working with a field being read by more than
        // one thread.
        this.lock.readLock().lock();
        try {
            cache = this.cache;
        } finally {
            this.lock.readLock().unlock();
        }

        // Get the new cache by applying changes to the old one. This will involve iterating over cache, but cache is
        // immutable so no lock is needed for iteration. See #change(Map, JsonArray).
        cache = change(cache, changes, this.prototype().preferIpAddress());

        // cache is now a fully immutable new Map. Install it and record the new stamp as a single atomic action. This
        // will block readers, such as uris() on the main thread.
        this.lock.writeLock().lock();
        try {
            this.cache = cache;
            this.stamp = newStamp;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /**
     * Returns the {@link Http1Client} used to communicate with a Eureka server.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * @return the non-{@code null} {@link Http1Client} used to communicate with a Eureka server
     */
    // Called only (indirectly) from replaceLoop().
    // Called only on fetchThread.
    private Http1Client client() {
        return this.client;
    }

    /**
     * Creates and returns a new, unstarted {@link Thread} that does not prevent the Java virtual machine from exiting
     * that is responsible for fetching information from a Eureka server.
     *
     * @return a new, non-{@code null}, unstarted {@link Thread} for communicating with Eureka
     *
     * @see #replaceLoop()
     */
    private Thread createFetchThread() {
        // The Runnable the fetch thread will execute. Normally this::replaceLoop.
        Runnable r;

        Http1ClientConfig clientPrototype = this.client().prototype();
        if (clientPrototype.baseUri().isEmpty()) {
            // Somewhat oddly, you can have a valid Http1ClientConfig with an empty Optional<ClientUri> as its baseUri()
            // property. When this is true, you can still use an Http1Client built from it even though you never set any
            // kind of endpoint address for it to talk to. So what *will* it connect to?  It turns out the URI your
            // client will then connect to in such a case is a synthetic one, namely http://localhost:80. Detect and
            // handle this state of affairs as early as possible.
            r = () -> {
                if (LOGGER.isLoggable(WARNING)) {
                    LOGGER.log(WARNING, "Required Eureka connectivity information not found in "
                               + clientPrototype + "; Eureka server will not be contacted");
                }
                // No need to close() here; the thread will exit normally but we don't want to start a new one.
            };
        } else {
            // Everything is normal. Use this::replaceLoop as the Thread's Runnable.
            r = this::replaceLoop;
        }

        // Return a new (happens-to-be-virtual) Thread (that does not prevent the VM from exiting) in an unstarted
        // state.
        return Thread.ofVirtual()
            .name(this.prototype().registryFetchThreadName())
            .uncaughtExceptionHandler(this::handleUncaughtFetchThreadThrowable)
            .unstarted(r);
    }

    /**
     * Handles {@link Throwable}s encountered by the {@link Runnable} {@linkplain Runnable#run() run} by the {@linkplain
     * #fetchThread fetch thread} by logging the error and {@linkplain #close() closing} this {@link
     * EurekaDiscoveryImpl}.
     *
     * @param fetchThread the fetch thread; ignored
     *
     * @param e a {@link Throwable}; the contract for {@link Thread.UncaughtExceptionHandler#uncaughtException(Thread,
     * Throwable)} does not forbid this from being {@code null}
     *
     * @see Thread.Builder#uncaughtExceptionHandler(Thread.UncaughtExceptionHandler)
     *
     * @see #createFetchThread()
     */
    private void handleUncaughtFetchThreadThrowable(Thread fetchThread, Throwable e) {
        if (e != null && LOGGER.isLoggable(ERROR)) {
            LOGGER.log(ERROR, e.getMessage(), e);
        }
        // The fetch thread has died due to an error; make sure we're closed cleanly.
        this.close();
    }

    /**
     * Returns a non-{@code null}, immutable, {@link SequencedSet} of {@link Instance}s corresponding to the supplied
     * discovery name, sourced from this {@link EurekaDiscoveryImpl}l's {@linkplain #cache cache}.
     *
     * <p>This method is idempotent and safe for concurrent use by multiple threads.</p>
     *
     * <p>This method will be called only when {@link EurekaDiscoveryConfig#cache()} returns {@code true}.</p>
     *
     * @param discoveryName a discovery name; must not be {@code null}
     *
     * @return a non-{@code null}, immutable, {@link SequencedSet} of {@link Instance}s corresponding to the supplied
     * discovery name, sourced from this {@link EurekaDiscoveryImpl}l's {@linkplain #cache cache}
     *
     * @exception NullPointerException if {@code discoveryName} is {@code null}
     *
     * @see #cache
     *
     * @see #instancesFunction
     */
    private SequencedSet<Instance> instancesFromCache(String discoveryName) {
        SequencedSet<Instance> instances;
        this.lock.readLock().lock();
        try {
            instances = this.cache.get(discoveryName.toUpperCase(Locale.ROOT));
        } finally {
            this.lock.readLock().unlock();
        }
        // (instances will be unmodifiable; no need to wrap it)
        return instances == null ? emptySequencedSet() : instances;
    }

    /**
     * Returns a non-{@code null}, immutable, {@link SequencedSet} of {@link Instance}s corresponding to the supplied
     * discovery name, sourced directly from the Eureka server, without applying any local caching semantics.
     *
     * <p>This method is idempotent and safe for concurrent use by multiple threads.</p>
     *
     * <p>This method will be called only when {@link EurekaDiscoveryConfig#cache()} returns {@code false}.</p>
     *
     * @param discoveryName a discovery name; must not be {@code null}
     *
     * @return a non-{@code null}, immutable, {@link SequencedSet} of {@link Instance}s corresponding to the supplied
     * discovery name, sourced directly from the Eureka server
     *
     * @exception NullPointerException if {@code discoveryName} is {@code null}
     *
     * @see #instancesFunction
     *
     * @see #fetchInstances(Http1Client, String)
     *
     * @see EurekaDiscoveryConfig#cache()
     */
    private SequencedSet<Instance> instancesFromServer(String discoveryName) {
        // Any errors encountered will have already been logged.
        return fetchInstances(this.client(), discoveryName)
            .map(i -> instances(i, this.prototype().preferIpAddress()))
            .orElseGet(EurekaDiscoveryImpl::emptySequencedSet);
    }

    /**
     * Fetches a new collection of application and instance information from the Eureka server and fully replaces the
     * current cache with it.
     *
     * <p>This method is called only from the {@linkplain #fetchThread fetch thread}.</p>
     *
     * <p>This method will be called only when {@link EurekaDiscoveryConfig#cache()} returns {@code false}.</p>
     *
     * @see #replaceLoop()
     *
     * @see EurekaDiscoveryConfig#cache()
     */
    private void replaceAll() {
        JsonObject applicationsObject = fetchAllApplications(this.client()).orElse(null);
        if (applicationsObject == null) {
            // There was some kind of error (already logged); do nothing on purpose.
            return;
        }
        // Eureka returns a string in the JSON called "apps__hashcode" which is a pseudo-hash of its contents (we'll
        // call it a "stamp"). Optimization: If it hasn't changed since last time, there is no replacement that needs to
        // happen. Stamps are critical when Eureka's so-called "deltas" are involved, which are a primitive form of
        // optimistic versioning.
        Object newStamp = applicationsObject.getString("apps__hashcode", "");
        if (this.applied(newStamp)) {
            // This fetch resulted in the same thing we already have. No replacement needs to occur.
            if (LOGGER.isLoggable(DEBUG)) {
                LOGGER.log(DEBUG, "Received redundant replacement; no action will be taken");
            }
            return;
        }
        JsonArray applicationArray = applicationsObject.getJsonArray("application");
        if (applicationArray == null) {
            if (LOGGER.isLoggable(ERROR)) {
                LOGGER.log(ERROR, "Received malformed JSON: " + applicationsObject + "; no action will be taken");
            }
            return;
        }
        // Even if applicationArray.isEmpty() is true, we go forward with the replacement logic, because it should be,
        // effectively, a "remove all" operation.
        Map<String, SequencedSet<Instance>> replacement = instancesMap(applicationArray, this.prototype().preferIpAddress());
        this.lock.writeLock().lock();
        try {
            this.cache = replacement;
            this.stamp = newStamp;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /**
     * While this {@link EurekaDiscoveryImpl} is not {@linkplain #close() closed}, periodically attempts to fetch
     * information from Eureka and to apply it to the local collection of such information stored in the {@link
     * #cache} instance field.
     *
     * <p>This method serves, by reference, as the {@link Runnable} used by the {@link #createFetchThread()} method. No
     * other code invokes it and none should.</p>
     *
     * <p>This method is called only from the {@linkplain #fetchThread fetch thread}.</p>
     *
     * @see #createFetchThread()
     *
     * @see #close()
     *
     * @see EurekaDiscoveryConfig#registryFetchChanges()
     *
     * @see EurekaDiscoveryConfig#registryFetchInterval()
     *
     * @see #fetchAll
     *
     * @see #replaceAll()
     *
     * @see #applied(Object)
     *
     * @see #change(Object, JsonArray)
     */
    // See createFetchThread().
    // Called only on fetchThread, as its Runnable/entry point.
    // Normally run every 30 seconds.
    private void replaceLoop() {
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, "Entering registry fetch loop; preparing to contact Eureka using " + this.client().prototype());
        }
        while (!this.closed) { // volatile read
            boolean fetchAll = this.fetchAll; // non-volatile read, but only this thread reads and writes it
            if (fetchAll) {
                // It's the first time, or the user said always get the whole catalog, or a previous attempt to retrieve
                // only changes resulted in a special "you can't have them" result that isn't, strictly speaking, an
                // error.
                this.replaceAll();
                if (this.prototype().registryFetchChanges()) {
                    // The user said to fetch changes only (after the first time).
                    this.fetchAll = false; // non-volatile write, but only this thread reads and writes it
                }
            } else {
                // The user said it was OK to fetch changes only.
                JsonObject changeSet = this.fetchChanges(this.client()).orElse(null);
                if (changeSet == null) {
                    // Sometimes Eureka will (effectively) return null here to indicate that yes, you asked for changes,
                    // but no, you can't have them, for a variety of mostly arcane reasons, but also no, no actual error
                    // occurred on your part or the server's. This is different from "there are no changes to be
                    // applied". In such a case you're supposed to double back and get everything in full. (The server
                    // cannot do this for you, because they happen to reuse the same data structure for both cases, and
                    // then there's no way to tell which entries represent changes and which represent full state.)
                    if (LOGGER.isLoggable(DEBUG)) {
                        LOGGER.log(DEBUG,
                                   "Eureka refused a request for changes; "
                                   + "changing all subsequent requests to full replacement requests");
                    }
                    this.fetchAll = true; // non-volatile write, but only this thread reads and writes it
                } else {
                    JsonArray changes = changeSet.getJsonArray("application");
                    if (changes.isEmpty()) {
                        // There are no changes to apply.
                        if (LOGGER.isLoggable(DEBUG)) {
                            LOGGER.log(DEBUG, "Received no changes to apply; no action will be taken");
                        }
                    } else {
                        Object newStamp = changeSet.getString("apps__hashcode", "");
                        if (this.applied(newStamp)) {
                            // The change set reported a "stamp" that we already have. This means we already applied the
                            // change set's changes against the cache identified by the stamp. No need to do so again.
                            if (LOGGER.isLoggable(DEBUG)) {
                                LOGGER.log(DEBUG,
                                           "Received changes that were already applied (" + changes + "); "
                                           + "no action will be taken");
                            }
                        } else {
                            // There are new changes. Apply them.
                            this.change(newStamp, changes);
                        }
                    }
                }
            }
            // Sleep for a configurable duration, and go around again. Handle genuine interruptions spurious wakeups
            // properly.
            try {
                Thread.sleep(this.prototype().registryFetchInterval());
            } catch (InterruptedException e) {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, "Registry fetch thread (" + Thread.currentThread().getName() + ") interrupted");
                }
                Thread.currentThread().interrupt();
            }
        }
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, "Exiting registry fetch loop");
        }
    }

    /**
     * If {@linkplain EurekaDiscoveryConfig#cache() caching is enabled}, and the current {@linkplain #fetchThread fetch
     * thread} is not already created, {@linkplain #createFetchThread() creates it} and {@linkplain Thread#start()
     * starts} it.
     *
     * <p>In all other cases, this method deliberately does nothing.</p>
     *
     * <p>This method is idempotent and safe for concurrent use by multiple threads.</p>
     *
     * <p>This method is called only from the {@link #uris(String, UriFactory, URI)} method.</p>
     */
    private void start() {
        if (!this.prototype().cache()) {
            // No need to create or start a thread.
            return;
        }
        Thread fetchThread = this.fetchThread.get();
        if (fetchThread != null) {
            // The thread is already up.
            return;
        }
        fetchThread = this.createFetchThread(); // non-null, unstarted
        if (!this.fetchThread.compareAndSet(null, fetchThread)) {
            // Someone else got there first.
            return;
        }
        this.closed = false; // volatile write
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, "Starting registry fetch thread (" + fetchThread.getName() + ")");
        }
        fetchThread.start();
    }

    /**
     * Called by the ({@code public}) {@link #uris(String, URI)} method, uses an additional {@link UriFactory} to
     * convert Eureka-supplied host-and-port information into {@link URI} objects.
     *
     * <p>This method implements the contract described by the documentation of the {@link #uris(String, URI)} method.</p>
     *
     * @param discoveryName a discovery name; must not be {@code null}; will be handled case-insensitively
     *
     * @param uriFactory a {@link UriFactory}; must not be {@code null}
     *
     * @param defaultValue a {@link URI} to use as a default; must not be {@code null}
     *
     * @return a non-{@code null}, immutable, {@link SequencedSet} of {@link DiscoveredUri} instances in conformance
     * with the {@link #uris(String, URI)} contract
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see #instancesFunction
     *
     * @see #instancesFromCache(String)
     *
     * @see #instancesFromServer(String)
     *
     * @see Instance#uris(UriFactory)
     */
    // Called only by the public two-argument form of uris() (see above). uriFactory turns a host/secure/port tuple into
    // a URI. See Instance#uris(UriFactory).
    private SequencedSet<DiscoveredUri> uris(String discoveryName, UriFactory uriFactory, URI defaultValue) {
        this.start(); // idempotent; no effect if caching is disabled
        SequencedSet<DiscoveredUri> discoveredSet = new LinkedHashSet<>();
        this.instancesFunction.apply(discoveryName).forEach(i -> discoveredSet.addAll(i.uris(uriFactory)));
        discoveredSet.addLast(new Uri(defaultValue));
        return unmodifiableSequencedSet(discoveredSet);
    }


    /*
     * Static methods.
     */


    /**
     * Calls the {@link #fetchApplicationsObject(Http1Client, String)} method with the supplied {@link Http1Client} and
     * {@code /v2/apps} as the argument for the {@code path} parameter and returns the result.
     *
     * <p>Note that the structure and format of the data returned by a Eureka server, version 2, in response to {@code
     * GET} methods, is <a href="https://github.com/Netflix/eureka/wiki/Eureka-REST-operations">undocumented</a>.</p>
     *
     * <p>This method is called only from the fetch thread.</p>
     *
     * @param client an {@link Http1Client}; must not be {@code null}
     *
     * @return a non-{@code null} {@link Optional} containing the response if one could be obtained
     *
     * @exception NullPointerException if {@code client} is {@code null}
     *
     * @see #fetchApplicationsObject(Http1Client, String)
     */
    // Static for testing.
    static Optional<JsonObject> fetchAllApplications(Http1Client client) {
        if (LOGGER.isLoggable(INFO)) {
            LOGGER.log(INFO, "Fetching applications from registry");
        }
        return fetchApplicationsObject(client, "/v2/apps");
    }

    /**
     * Uses the supplied {@link Http1Client} to issue an HTTP {@code GET} method against a Eureka server endpoint ending
     * with the supplied {@code path}, normally {@code /v2/apps} or {@code /v2/apps/delta}, expecting {@linkplain
     * io.helidon.common.media.type.MediaTypes#APPLICATION_JSON JSON} to be returned representing
     * <dfn>applications</dfn>.
     *
     * <p>Note that the structure and format of the data returned by a Eureka server, version 2, in response to {@code
     * GET} methods, is <a href="https://github.com/Netflix/eureka/wiki/Eureka-REST-operations">undocumented</a>.</p>
     *
     * <p>This method is called only from the fetch thread.</p>
     *
     * <p>This method is idempotent and safe for concurrent use by multiple threads.</p>
     *
     * @param client an {@link Http1Client}; must not be {@code null}
     *
     * @param path a path; normally {@code /v2/apps} or {@code /v2/apps/delta}; must not be {@code null}
     *
     * @return a non-{@code null} {@link Optional} containing the result if one could be obtained
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see <a href="https://github.com/Netflix/eureka/wiki/Eureka-REST-operations">Eureka REST operations</a>
     */
    // Static for testing.
    static Optional<JsonObject> fetchApplicationsObject(Http1Client client, String path) {
        try (var response = client
             .get(path)
             .accept(APPLICATION_JSON)
             .header(ACCEPT_ENCODING, "gzip")
             .request()) {
            switch (response.status().code()) {
            case 200: // success, entity expected
                // Reverse engineered:
                //
                //   { "appplications": { // <-- this object is returned
                //       "apps__hashcode": "",
                //       // ...
                //       "application": [
                //         // ...
                //       ]
                //     }
                //   }
                ReadableEntity e = response.entity();
                JsonObject entity;
                if (e.hasEntity()) {
                    entity = e
                        .as(JsonObject.class)
                        .getJsonObject("applications");
                    if (LOGGER.isLoggable(INFO)) {
                        JsonArray applications = entity.getJsonArray("application");
                        LOGGER.log(INFO, "Retrieved " + applications.size() + " applications");
                    }
                    if (LOGGER.isLoggable(DEBUG)) {
                        // This could be pretty darn big
                        LOGGER.log(DEBUG, entity.toString());
                    }
                } else {
                    // Note that Eureka can return a 200 with no entity. This is either invalid HTTP or bad practice but
                    // Eureka can do it so we have to handle it.
                    if (LOGGER.isLoggable(WARNING)) {
                        LOGGER.log(WARNING, "Retrieved no applications; Eureka returned a status of 200 but supplied no content");
                    }
                    entity = null;
                }
                return Optional.ofNullable(entity);
            case 403:
                // See
                // https://github.com/Netflix/eureka/blob/v2.0.5/eureka-core/src/main/java/com/netflix/eureka/resources/ApplicationsResource.java#L134-L139
                // and
                // https://github.com/Netflix/eureka/blob/v2.0.5/eureka-core/src/main/java/com/netflix/eureka/resources/ApplicationsResource.java#L206-L210
                if (LOGGER.isLoggable(INFO)) {
                    LOGGER.log(INFO, "Retrieved nothing; the server has been configured to deny access to " + path);
                }
                return Optional.empty();
            default:
                if (LOGGER.isLoggable(WARNING)) {
                    LOGGER.log(WARNING, "Retrieved no applications. Unexpected status: " + response.status().code());
                    e = response.entity();
                    if (e.hasEntity()) {
                        LOGGER.log(WARNING, "  Entity: " + e.as(String.class));
                    }
                }
                return Optional.empty();
            }
        } catch (IllegalStateException e) {
            // client is somehow (presumably irrevocably) closed. There is no way to check for this condition prior to
            // issuing a call. See implementation of Http1ClientImpl#closeResource().
            if (LOGGER.isLoggable(ERROR)) {
                LOGGER.log(ERROR, e.getMessage(), e);
            }
            return Optional.empty();
        } catch (UncheckedIOException e) {
            // Per contract, connectivity issues must not stop the application from functioning.
            if (LOGGER.isLoggable(ERROR)) {
                LOGGER.log(ERROR, e.getMessage(), e);
            }
            return Optional.empty();
        }
    }

    /**
     * Calls the {@link #fetchApplicationsObject(Http1Client, String)} method with the supplied {@link Http1Client} and
     * {@code /v2/apps/delta} as the argument for the {@code path} parameter and returns the result.
     *
     * <p>Note that the structure and format of the data returned by a Eureka server, version 2, in response to {@code
     * GET} methods, is <a href="https://github.com/Netflix/eureka/wiki/Eureka-REST-operations">undocumented</a>.</p>
     *
     * <p>This method is called only from the fetch thread.</p>
     *
     * @param client an {@link Http1Client}; must not be {@code null}
     *
     * @return a non-{@code null} {@link Optional} containing the response if one could be obtained
     *
     * @exception NullPointerException if {@code client} is {@code null}
     *
     * @see #fetchApplicationsObject(Http1Client, String)
     */
    // Static for testing.
    static Optional<JsonObject> fetchChanges(Http1Client client) {
        // From Eureka's ApplicationResource.java:
        //
        // The delta changes represent the registry information change for a period as configured by
        // EurekaServerConfig#getRetentionTimeInMSInDeltaQueue(). The changes that can happen in a registry include
        // Registrations,Cancels,Status Changes and Expirations. Normally the changes to the registry are infrequent and
        // hence getting just the delta will be much more efficient than getting the complete registry.
        if (LOGGER.isLoggable(INFO)) {
            LOGGER.log(INFO, "Fetching changes from registry");
        }
        return fetchApplicationsObject(client, "/v2/apps/delta");
    }

    /**
     * Uses the supplied {@link Http1Client} to issue an HTTP {@code GET} method against a Eureka server endpoint ending
     * with {@code /v2/apps/} suffixed with the {@linkplain String#toUpperCase(Locale) uppercase} representation of the
     * supplied {@code discoveryName}, expecting {@linkplain io.helidon.common.media.type.MediaTypes#APPLICATION_JSON
     * JSON} to be returned representing <dfn>instances</dfn> of the named <dfn>application</dfn>.
     *
     * <p>Note that the structure and format of the data returned by a Eureka server, version 2, in response to {@code
     * GET} methods, is <a href="https://github.com/Netflix/eureka/wiki/Eureka-REST-operations">undocumented</a>.</p>
     *
     * <p>This method is idempotent and safe for concurrent use by multiple threads.</p>
     *
     * @param client an {@link Http1Client}; must not be {@code null}
     *
     * @param discoveryName a discovery name; must not be {@code null}
     *
     * @return a non-{@code null} {@link Optional} containing the result if one could be obtained
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see <a href="https://github.com/Netflix/eureka/wiki/Eureka-REST-operations">Eureka REST operations</a>
     */
    // Static for testing.
    static Optional<JsonArray> fetchInstances(Http1Client client, String discoveryName) {
        discoveryName = discoveryName.toUpperCase(Locale.ROOT);
        try (var response = client
             .get("/v2/apps/" + discoveryName)
             .accept(APPLICATION_JSON)
             .header(ACCEPT_ENCODING, "gzip")
             .request()) {
            switch (response.status().code()) {
            case 200: // success, entity expected
                // Reverse engineered from GET /v2/apps/EXAMPLE:
                //
                //   { "appplication": {
                //       "name": "EXAMPLE",
                //       "instance": []  // <-- this array is what this method returns
                //     }
                //   }
                return Optional.of(response.entity()
                                   .as(JsonObject.class)
                                   .getJsonObject("application")
                                   .getJsonArray("instance"));
            case 404: // not found
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG,
                               "Retrieved no instances for " + discoveryName + "; "
                               + "application " + discoveryName + " not found");
                }
                return Optional.empty();
            default:
                if (LOGGER.isLoggable(WARNING)) {
                    LOGGER.log(WARNING,
                               "Retrieved no instances for " + discoveryName + "; "
                               + "unexpected status: " + response.status().code());
                    ReadableEntity e = response.entity();
                    if (e.hasEntity()) {
                        LOGGER.log(WARNING, "  Entity: " + e.as(String.class));
                    }
                }
                return Optional.empty();
            }
        } catch (IllegalStateException e) {
            // client is somehow (presumably irrevocably) closed. There is no way to check for this condition prior to
            // issuing a call. See implementation of Http1ClientImpl#closeResource().
            if (LOGGER.isLoggable(ERROR)) {
                LOGGER.log(ERROR, e.getMessage(), e);
            }
            return Optional.empty();
        } catch (UncheckedIOException e) {
            // Per contract, connectivity issues must not stop the application from functioning.
            if (LOGGER.isLoggable(ERROR)) {
                LOGGER.log(ERROR, e.getMessage(), e);
            }
            return Optional.empty();
        }
    }

    /**
     * Transforms a {@link JsonObject} representing a Eureka <dfn>instance</dfn> into an {@link Instance} object and
     * returns it as an {@link Optional}.
     *
     * <p>This method is called only from the fetch thread.</p>
     *
     * <p>Note that the structure and format of the data returned by a Eureka server, version 2, in response to {@code
     * GET} methods, including structures representing instances, is <a
     * href="https://github.com/Netflix/eureka/wiki/Eureka-REST-operations">undocumented</a>.</p>
     *
     * @param jsonInstance a {@link JsonObject} representing a Eureka instance; must not be {@code null}
     *
     * @param preferIpAddress if the <dfn>host</dfn> component of URIs should be set to the stored IP address instead of
     * the stored hostname
     *
     * @return a non-{@code null} {@link Optional} housing an {@link Instance}, if available
     *
     * @exception NullPointerException if {@code jsonInstance} is {@code null}
     */
    // Static for testing.
    static Optional<Instance> instance(JsonObject jsonInstance, boolean preferIpAddress) {
        // Reverse engineered; from GET /V2/apps:
        //
        //   { "appplications": {
        //       "application": [
        //         { "name": "EXAMPLE",
        //           "instance": [
        //             { "instanceId": "ABCXYZ", // <-- the JSON object this method works with starts here; partial contents
        //               "hostname": "localhost",
        //               "app": "EXAMPLE",
        //               "ipAddr": "127.0.0.1",
        //               "status": "UP",
        //               "metadata": {
        //                 "someKey": "someValue"
        //               },
        //               "overriddenstatus": "DOWN",
        //               "securePort": {
        //                  "$": 443,
        //                  "@enabled": true
        //               },
        //               "port": {
        //                 "$": 80,
        //                 "@enabled": false
        //               }
        //               // ...
        //             }
        //           ]
        //         }
        //       ]
        //     }
        //   }
        //
        // From GET /v2/apps/EXAMPLE:
        //
        //   { "application": {
        //       "name": "EXAMPLE",
        //       "instance": [
        //         { "instanceId": "ABCXYZ", // <-- same JSON object as above
        //           "hostname": "localhost"
        //         // ...
        //         }
        //       ]
        //     }
        //   }
        String host = jsonInstance.getString(preferIpAddress ? "ipAddr" : "hostName");
        if (host == null) {
            // Technically possible. We're supposed to ignore it.
            return Optional.empty();
        }
        Instance.Status status;
        try {
            status = Instance.Status.valueOf(jsonInstance.getString("status", "UNKNOWN").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            status = Instance.Status.UNKNOWN;
        }

        Map<String, String> metadata = new HashMap<>(metadata(jsonInstance.getJsonObject("metadata")));
        metadata.put("io.helidon.discovery.status", status.name());
        // (Yes, there can be two overridden status fields; yes, their case is just as it appears here.)
        String overriddenStatus = jsonInstance.getString("overriddenstatus", jsonInstance.getString("overriddenStatus"));
        if (overriddenStatus != null && !overriddenStatus.equalsIgnoreCase("UNKNOWN")) {
            // Eureka can default the overriddenstatus and overriddenStatus fields (yes, there can be two of them, yes,
            // the case is just like that) to "UNKNOWN". This means the "real" status has not been overridden (!).
            try {
                metadata.put("io.helidon.discovery.status",
                             Instance.Status.valueOf(overriddenStatus.toUpperCase(Locale.ROOT)).name());
            } catch (IllegalArgumentException e) {
                if (LOGGER.isLoggable(DEBUG)) {
                    LOGGER.log(DEBUG, e.getMessage(), e);
                }
                metadata.put("io.helidon.discovery.status", "UNKNOWN");
            }
        }

        JsonObject portObject = jsonInstance.getJsonObject("securePort");
        int securePort = enabled(portObject) ? port(portObject) : -1;
        portObject = jsonInstance.getJsonObject("port");
        int nonSecurePort = enabled(portObject) ? port(portObject) : -1;
        return Optional.of(new Instance(jsonInstance.getString("instanceId"), host, securePort, nonSecurePort, status, metadata));
    }

    /**
     * Transforms a {@link JsonArray} of Eureka <dfn>application</dfn> structures into an immutable {@link Map} of
     * immutable {@link SequencedSet}s of {@link Instance}s indexed by application name, and returns it.
     *
     * <p>This method is idempotent.</p>
     *
     * <p>This method returns determinate values.</p>
     *
     * <p>This method is called only from the {@linkplain #fetchThread fetch thread}.</p>
     *
     * <p>Note that the structure and format of the data returned by a Eureka server, version 2, in response to {@code
     * GET} methods, including application structures, is <a
     * href="https://github.com/Netflix/eureka/wiki/Eureka-REST-operations">undocumented</a>.</p>
     *
     * @param applications a {@link JsonArray} representing Eureka-supplied application structures; if {@code null} then
     * {@link Map#of()} will be returned
     *
     * @param preferIpAddress if the <dfn>host</dfn> component of URIs should be set to the stored IP address instead of
     * the stored hostname
     *
     * @return an immutable {@link Map} of immutable {@link SequencedSet}s of {@link Instance}s indexed by application
     * name
     */
    // Static for testing.
    static Map<String, SequencedSet<Instance>> instancesMap(JsonArray applications, boolean preferIpAddress) {
        if (applications == null || applications.isEmpty()) {
            return Map.of();
        }
        Map<String, SequencedSet<Instance>> m = newHashMap(applications.size());
        for (JsonValue a : applications) {
            JsonObject application = a.asJsonObject();
            // Reverse engineered:
            //
            //   { "appplications": {
            //       "application": [
            //         { "name": "EXAMPLE",
            //           "instance": [] // <-- this array is what this method works on
            //         }
            //       ]
            //     }
            //   }
            String name = application.getString("name");
            if (name == null) {
                continue; // technically speaking Eureka can do this; skip any such payload
            }
            List<Instance> instances = new ArrayList<>();
            for (JsonValue jsonInstance : application.getJsonArray("instance")) {
                instance(jsonInstance.asJsonObject(), preferIpAddress).ifPresent(instances::add);
            }
            if (instances.size() > 1) {
                // Partially order the list by Instance.Status only.
                Collections.sort(instances, Instance.COMPARATOR);
            }
            m.computeIfAbsent(name, n -> new LinkedHashSet<>()).addAll(instances);
        }
        if (m.isEmpty()) {
            return Map.of();
        }
        m.replaceAll((k, v) -> unmodifiableSequencedSet(v));
        return unmodifiableMap(m);
    }

    /**
     * Transforms a {@link JsonArray} of Eureka <dfn>instance</dfn> structures into an immutable {@link SequencedSet} of
     * {@link Instance}s, and returns it.
     *
     * <p>This method is idempotent.</p>
     *
     * <p>This method returns determinate values.</p>
     *
     * <p>This method is called only from the {@linkplain #fetchThread fetch thread}.</p>
     *
     * @param jsonInstances a {@link JsonArray} representing Eureka-supplied instance structures; if {@code null} then
     * an {@linkplain SequencedSet#isEmpty() empty} immutable {@link SequencedSet} will be returned
     *
     * @param preferIpAddress if the <dfn>host</dfn> component of URIs should be set to the stored IP address instead of
     * the stored hostname
     *
     * @return an immutable {@link SequencedSet} of {@link Instance}s
     *
     * @see #instance(JsonObject, boolean)
     */
    // Static for testing.
    static SequencedSet<Instance> instances(JsonArray jsonInstances, boolean preferIpAddress) {
        return switch (jsonInstances == null ? 0 : jsonInstances.size()) {
        case 0 -> emptySequencedSet();
        case 1 -> instance(jsonInstances.getJsonObject(0), preferIpAddress)
            .map(i -> unmodifiableSequencedSet(new LinkedHashSet<>(List.of(i))))
            .orElse(emptySequencedSet());
        default -> {
            List<Instance> instances = new ArrayList<>();
            for (JsonValue jsonInstance : jsonInstances) {
                instance(jsonInstance.asJsonObject(), preferIpAddress).ifPresent(instances::add);
            }
            if (instances.size() > 1) {
                // Partially order the list by Instance.Status only.
                Collections.sort(instances, Instance.COMPARATOR);
            }
            yield unmodifiableSequencedSet(new LinkedHashSet<>(instances));
        }
        };
    }

    /**
     * Transforms a {@link JsonObject} representing a <dfn>metadata</dfn> structure supplied by Eureka into an immutable
     * {@link Map} of {@link String}-typed metadata values indexed by {@link String}-typed metadata keys, and returns it.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>This method is idempotent.</p>
     *
     * <p>This method is called only from the fetch thread.</p>
     *
     * <p>Note that the structure and format of the data returned by a Eureka server, version 2, in response to {@code
     * GET} methods, including metadata structures, is <a
     * href="https://github.com/Netflix/eureka/wiki/Eureka-REST-operations">undocumented</a>.</p>
     *
     * @param metadata a {@link JsonObject} representing a metadata structure; if {@code null} then {@link Map#of()}
     * will be returned
     *
     * @return an immutable, non-{@code null} {@link Map} of {@link String}-typed metadata values indexed by {@link
     * String}-typed metadata keys
     */
    // Static for testing. Turns a metadata-shaped JsonObject structure (string key/value pairs) into an unmodifiable Map.
    static Map<String, String> metadata(JsonObject metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, String> m = newHashMap(metadata.size());
        // Reverse engineered:
        //
        //   { "appplications": {
        //       "application": [
        //         { "name": "EXAMPLE",
        //           "instance": [
        //             { "metadata": { // <-- this object is what this method works on
        //                 "someKey": "someValue" // string values only, they claim
        //               },
        //   ...
        for (Entry<String, JsonValue> e : metadata.entrySet()) {
            JsonValue v = e.getValue();
            if (v == null) {
                continue;
            }
            switch (v.getValueType()) {
                // Vaules *should* be only strings but Eureka is undocumented so permit String representations of all
                // scalar values.
            case FALSE -> m.put(e.getKey(), "false");
            case NUMBER -> m.put(e.getKey(), v.toString()); // guaranteed to be equivalent to BigDecimal#toString() output
            case NULL -> m.put(e.getKey(), null);
            case STRING -> m.put(e.getKey(), ((JsonString) v).getString());
            case TRUE -> m.put(e.getKey(), "true");
            default -> {}
            }
        }
        return Collections.unmodifiableMap(m);
    }

    /*
     * Private static methods.
     */

    /**
     * Given a {@link Map} containing {@link SequencedSet}s of {@link Instance}s indexed by application name, applies
     * the changes represented by the supplied {@link JsonArray} and returns either the supplied {@code
     * existingInstances} {@link Map} (if and only if the supplied {@code changes} array is empty), or, most commonly, a
     * fully immutable {@link Map} representing the state that results.
     *
     * <p>This method never returns {@code null}.</p>
     *
     * <p>This method returns determinate values.</p>
     *
     * <p>This method is idempotent.</p>
     *
     * <p>This method is called only from the {@link #fetchThread fetch thread}.</p>
     *
     * @param existingInstances a {@link Map} of {@link SequencedSet}s of {@link Instance}s indexed by application name;
     * must not be {@code null}
     *
     * @param changes a {@link JsonArray} of changes to apply, represented as {@code application} objects as supplied by
     * the Eureka server; must not be {@code null}
     *
     * @param preferIpAddress if the <dfn>host</dfn> component of URIs should be set to the stored IP address instead of
     * the stored hostname
     *
     * @return an immutable {@link Map} of immutable {@link SequencedSet}s of {@link Instance}s representing the state
     * that results from applying the changes, or {@code existingInstances} if {@code changes} {@linkplain
     * JsonArray#isEmpty() is empty}
     *
     * @exception NullPointerException if any argument is {@code null}
     */
    // Non-private for testing.
    static Map<String, SequencedSet<Instance>> change(Map<String, SequencedSet<Instance>> existingInstances,
                                                      JsonArray changes,
                                                      boolean preferIpAddress) {
        if (changes.isEmpty()) {
            return requireNonNull(existingInstances, "existingInstances");
        }

        // Deliberately create a deeply mutable copy of the existing instances. Key order is not retained since it is
        // insignificant.
        Map<String, SequencedSet<Instance>> returnValue = newHashMap(existingInstances.size());
        for (Entry<String, SequencedSet<Instance>> e : existingInstances.entrySet()) {
            returnValue.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
        }

        for (JsonValue change : changes) {

            // Groups of changes (Eureka calls them "deltas"), for some reason, are represented by "application"
            // objects: the same "application" object used when you simply ask for one or more applications, but here
            // with additional semantics (here they represent actions taken, not descriptions of state). It looks like
            // they just wanted to reuse the existing JSON data structure for a totally different purpose.
            JsonObject application = change.asJsonObject();
            String applicationName = application.getString("name");
            if (applicationName == null) {
                // Technically possible; you're supposed to ignore it in this case.
                if (LOGGER.isLoggable(WARNING)) {
                    LOGGER.log(WARNING, "Skipping unnamed changes: " + application);
                }
                continue;
            }

            // Similarly, an individual change within a group is represented by an "instance", with a field called
            // "actionType" that is significant in this case (when you simply retrieve an "application" and look at an
            // "instance" it contains, the "actionType" field does not seem to be significant). None of this is
            // documented.
            JsonArray instances = application.getJsonArray("instance");
            int size = instances == null ? 0 : instances.size();
            if (size == 0) {
                // (Simple optimization.)
                continue;
            }

            // The "deltas" model that Eureka uses is underspecified. If you get an addition of X followed by a deletion
            // of X, should this result in nothing happening? If you get a deletion of X followed by a modification of
            // X, does this result in nothing happening? Is this even a possible state? And so on. We take our best
            // shot, looking to what the Eureka-authored client does, but it has at least one known issue in this area
            // (https://github.com/Netflix/eureka/issues/1528).

            List<Instance> additions = new ArrayList<>(size);
            List<Instance> modifications = new ArrayList<>(size);
            List<Instance> deletions = new ArrayList<>(size);
            for (JsonValue i : instances) {
                JsonObject jsonInstance = i.asJsonObject();
                // See https://github.com/Netflix/eureka/blob/v2.0.5/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L1354-L1359
                switch (jsonInstance.getString("actionType", "UNKNOWN").toUpperCase(Locale.ROOT)) {
                case "ADDED"    -> instance(jsonInstance, preferIpAddress).ifPresent(additions::add);
                case "MODIFIED" -> instance(jsonInstance, preferIpAddress).ifPresent(modifications::add);
                case "DELETED"  -> instance(jsonInstance, preferIpAddress).ifPresent(deletions::add);
                default         -> {}
                }
            }

            // Get the mutable set of instances stored (or to be stored) under the application name.
            SequencedSet<Instance> instanceSet = returnValue.computeIfAbsent(applicationName, x -> new LinkedHashSet<>());

            // Do deletions first. It is undocumented whether changes with an actionType of DELETED are supposed to
            // apply only to pre-existing instances, or other changes in the set. Common sense says they should apply to
            // pre-existing instances, so if we do them first there will be no chance of accidentally deleting incoming
            // changes.
            if (!deletions.isEmpty() && !instanceSet.isEmpty()) {
                for (Instance deletion : deletions) {
                    instanceSet.remove(deletion);
                }
            }

            // Keep track of whether we need to re-sort the SequencedSet.
            boolean sort = false;

            // Do modifications next for the same class of reasons. We will remove any matching entry, and add the
            // "modified" entry. We will re-sort the set after all changes are applied.
            if (!modifications.isEmpty() && !instanceSet.isEmpty()) {
                for (Instance modification : modifications) {
                    instanceSet.remove(modification);
                    // This is interesting. Suppose the removal immediately above didn't actually remove anything
                    // (i.e. the change was an instance that existingInstances didn't actually contain). Common sense
                    // dictates this shouldn't happen but there is nothing to prevent it. If so, then should the add
                    // proceed?  Eureka treats modifications exactly the same as additions (!) so seemingly the answer
                    // is yes. See https://github.com/Netflix/eureka/issues/1602.
                    instanceSet.add(modification);
                    if (!sort) {
                        // A status may have changed so we may have to re-order the set eventually.
                        sort = true;
                    }
                }
            }

            // Do additions last. This is trickier than it first looks. An instance is deliberately identified only by
            // its ID, so a simple add into the instanceSet might fail, since there might be one in there already under
            // the same ID. But we'd like to use the *new* instance in this case, because its status and other
            // non-normative bits may be different from what was stored there before. So, as with modifications, we
            // first blindly attempt a removal followed by an addition. Note that Eureka essentially does the same
            // thing, possibly without realizing it. See https://github.com/Netflix/eureka/issues/1602.
            if (!additions.isEmpty()) {
                for (Instance addition : additions) {
                    instanceSet.remove(addition); // see comment above
                    instanceSet.add(addition);
                    if (!sort) {
                        sort = true;
                    }
                }
            }

            if (instanceSet.isEmpty()) {
                // We deleted everything under this application.
                returnValue.remove(applicationName);
            } else if (sort && instanceSet.size() > 1) {
                // Partially re-order the set by status if necessary.
                List<Instance> l = new ArrayList<>(instanceSet);
                Collections.sort(l, Instance.COMPARATOR);
                instanceSet.clear();
                instanceSet.addAll(l);
            }

        }

        // Ensure that what we return is completely immutable.
        if (returnValue.isEmpty()) {
            return Map.of();
        }
        returnValue.replaceAll((k, v) -> Collections.unmodifiableSequencedSet(v));
        return Collections.unmodifiableMap(returnValue);
    }

    /**
     * Returns a non-{@code null}, immutable, {@linkplain SequencedSet#isEmpty() empty} {@link SequencedSet}.
     *
     * @param <T> the element type
     *
     * <p>This method is idempotent and safe for concurrent use by multiple threads.</p>
     *
     * @return a non-{@code null}, immutable, {@linkplain SequencedSet#isEmpty() empty} {@link SequencedSet}
     */
    @SuppressWarnings("unchecked")
    private static <T> SequencedSet<T> emptySequencedSet() {
        return (SequencedSet<T>) EMPTY_SEQUENCED_SET;
    }

    /**
     * Returns {@code true} if and only if the supplied {@link JsonObject} is non-{@code null} and {@linkplain
     * JsonObject#getBoolean(String, boolean) contains a <code>boolean</code> field} named {@code @enabled} whose value
     * is {@code true}.
     *
     * @param portObject a {@link JsonObject} representing a Eureka-supplied port structure; may be {@code null} in
     * which case {@code false} will be returned
     *
     * @return {@code true} if and only if the port represented by the supplied {@link JsonObject} is to be considered
     * <dfn>enabled</dfn>
     */
    // Turns a weird port-information-shaped JsonObject into a boolean saying whether it is "enabled" or not.
    private static boolean enabled(JsonObject portObject) {
        // Yes, Eureka returns booleans in this case as lowercase strings.
        return portObject != null && Boolean.parseBoolean(portObject.getString("@enabled", "false"));
    }

    /**
     * Returns the port number represented by the supplied {@link JsonObject}, if it is non-{@code null} and {@linkplain
     * JsonObject#getBoolean(String, boolean) contains a numeric field} named {@code $}, or a value less than {@code 0}
     * in all other cases.
     *
     * @param portObject a {@link JsonObject} representing a Eureka-supplied port structure; may be {@code null} in
     * which case a negative will be returned
     *
     * @return a (zero or positive) port number, or a negative value
     */
    // Turns a weird port-information-shaped JsonObject into a port number.
    private static int port(JsonObject portObject) {
        return portObject == null ? -1 : portObject.getInt("$", -1);
    }


    /*
     * Inner and nested classes.
     */


    /**
     * A record holding Eureka-supplied information about <dfn>endpoints</dfn>.
     *
     * @param id a unique identifier for this {@link Instance}; must not be {@code null}
     *
     * @param host a hostname for this {@link Instance}; must not be {@code null}
     *
     * @param securePort a <dfn>secure port</dfn> for this {@link Instance}; a negative value indicates the port is not
     * <dfn>enabled</dfn>
     *
     * @param nonSecurePort a <dfn>non-secure port</dfn> for this {@link Instance}; a negative value indicates the port
     * is not <dfn>enabled</dfn>
     *
     * @param status a {@link Status} for this {@link Instance}; if {@code null}, {@link Status#UNKNOWN} will be used
     * instead
     *
     * @param metadata a {@link Map} of {@link String}-typed metadata values indexed by {@link String}-typed metadata
     * keys; if {@code null} an empty {@link Map} will be used instead
     */
    // A record housing Eureka information about endpoints for an application.
    // Non-private for testing
    record Instance(String id,
                    String host,
                    int securePort,
                    int nonSecurePort,
                    Status status,
                    Map<String, String> metadata) {

        /**
         * A {@link Comparator} that <strong>only partially orders</strong> {@link Instance}s by their {@linkplain
         * #status() statuses}.
         *
         * <p>Statuses that are deemed to be "more available", in the mostly undefined fashion used by Eureka, precede
         * others.</p>
         *
         * @see <a
         * href="https://github.com/Netflix/eureka/wiki/Understanding-eureka-client-server-communication#about-instance-statuses">About
         * Instance Statuses</a>
         *
         * @see Status
         */
        // NOT consistent with equals; partial order only
        private static final Comparator<Instance> COMPARATOR =
            new Comparator<>() {
                @Override
                public int compare(Instance i0, Instance i1) {
                    if (i0 == i1) {
                        return 0;
                    }
                    return (i0 == null ? Status.UNKNOWN : i0.status()).compareTo(i1 == null ? Status.UNKNOWN : i1.status());
                }
            };

        /**
         * Creates a new {@link Instance}.
         *
         * @param id a unique identifier for this {@link Instance}; must not be {@code null}
         *
         * @param host a hostname for this {@link Instance}; must not be {@code null}
         *
         * @param securePort a <dfn>secure port</dfn> for this {@link Instance}; a negative value indicates the port is
         * not <dfn>enabled</dfn>
         *
         * @param nonSecurePort a <dfn>non-secure port</dfn> for this {@link Instance}; a negative value indicates the
         * port is not <dfn>enabled</dfn>
         *
         * @param status a {@link Status} for this {@link Instance}; if {@code null}, {@link Status#UNKNOWN} will be
         * used instead
         *
         * @param metadata a {@link Map} of {@link String}-typed metadata values indexed by {@link String}-typed
         * metadata keys; if {@code null} an empty {@link Map} will be used instead
         *
         * @exception NullPointerException if {@code id} or {@code host} is {@code null}
         */
        // Non-private for testing.
        Instance {
            requireNonNull(id, "id");
            requireNonNull(host, "host");
            if (status == null) {
                status = Status.UNKNOWN;
            }
            metadata = metadata == null || metadata.isEmpty() ? Map.of() : Map.copyOf(metadata);
        }

        /**
         * Returns {@code true} if and only if the supplied {@link Object} is an {@link Instance} whose {@link #id() id}
         * component {@linkplain String#equals(Object) is equal to} this {@link Instance}'s {@link #id() id} component.
         *
         * @param other an {@link Object}; may be {@code null} in which case {@code false} will be returned
         *
         * @return {@code true} if and only if the supplied {@link Object} is an {@link Instance} whose {@link #id() id}
         * component {@linkplain String#equals(Object) is equal to} this {@link Instance}'s {@link #id() id} component
         */
        @Override // Record
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            } else if (other != null && other.getClass() == this.getClass()) {
                // Use id only.
                return this.id().equals(((Instance) other).id());
            } else {
                return false;
            }
        }

        /**
         * Returns a hashcode for this {@link Instance} deliberately computed solely from its {@link #id() id} component
         * and no other state.
         *
         * @return a hashcode
         */
        @Override // Record
        public int hashCode() {
            // Use id only.
            return this.id().hashCode();
        }

        /**
         * Returns a new, non-{@code null}, immutable {@link List} of {@link DiscoveredUri}s that this {@link Instance}
         * represents, using the supplied {@link UriFactory} to construct valid {@link URI}s.
         *
         * <p>This method returns determinate values when the supplied {@link UriFactory} returns determinate
         * values.</p>
         *
         * @param f a {@link UriFactory}; must not be {@code null}
         *
         * @return a new, non-{@code null}, immutable {@link List} of {@link DiscoveredUri}s
         *
         * @exception NullPointerException if {@code f} is {@code null}
         *
         * @see UriFactory#uri(String, int, boolean, Map)
         */
        private List<DiscoveredUri> uris(UriFactory f) {
            String host = this.host();
            int securePort = this.securePort();
            int nonSecurePort = this.nonSecurePort();
            Map<String, String> md = this.metadata();
            URI secureUri =
                securePort < 0 ? null : f.uri(host, securePort, true, md).orElse(null);
            URI nonSecureUri =
                nonSecurePort < 0 ? null : f.uri(host, nonSecurePort, false, md).orElse(null);
            if (secureUri == null) {
                return nonSecureUri == null ? List.of() : List.of(new Uri(nonSecureUri, md));
            } else if (nonSecureUri == null) {
                return List.of(new Uri(secureUri, md));
            }
            return List.of(new Uri(secureUri, md), new Uri(nonSecureUri, md));
        }

        /**
         * A pseudo-health <dfn>status</dfn> of a Eureka-supplied <dfn>instance</dfn>.
         *
         * @see <a
         * href="https://github.com/Netflix/eureka/wiki/Understanding-eureka-client-server-communication#about-instance-statuses">About
         * Instance Statuses</a>
         */
        // Non-private for testing.
        enum Status {

            // **Declaration order is significant.**
            //
            // More available statuses come before less available ones.
            //
            // See
            // https://github.com/Netflix/eureka/blob/v2.0.5/eureka-client/src/main/java/com/netflix/appinfo/InstanceInfo.java#L314-L320.

            /**
             * A {@link Status} representing an instance whose endpoints are available in some undefined fashion.
             */
            UP,

            /**
             * A {@link Status} representing an instance whose endpoints are most likely going to become available, in
             * some undefined fashion, at some point in the future.
             */
            STARTING,

            /**
             * A {@link Status} representing an instance whose endpoints were not available, in some undefined fashion,
             * at some point in the past.
             */
            DOWN,

            /**
             * A {@link Status} representing an instance whose endpoints have been taken out of service in some
             * undefined fashion, either temporarily or permanently.
             */
            OUT_OF_SERVICE,

            /**
             * A {@link Status} representing an instance whose endpoints' availability is entirely unknown.
             */
            UNKNOWN;
        }

    }

    /**
     * A {@link DiscoveredUri} implementation.
     *
     * @param uri a {@link URI}; must not be {@code null}
     *
     * @param metadata a {@link Map} of {@link String}-typed metadata values indexed by {@link String}-typed metadata
     * keys; if {@code null} an empty {@link Map} will be used instead
     */
    // A DiscoveredUri implementation.
    private record Uri(URI uri, Map<String, String> metadata) implements DiscoveredUri {

        /**
         * Creates a new {@link Uri}.
         *
         * @param uri a {@link URI}; must not be {@code null}
         *
         * @exception NullPointerException if {@code uri} is {@code null}
         */
        private Uri(URI uri) {
            this(uri, Map.of());
        }

        /**
         * Creates a new {@link Uri}.
         *
         * @param uri a {@link URI}; must not be {@code null}
         *
         * @param metadata a {@link Map} of {@link String}-typed metadata values indexed by {@link String}-typed
         * metadata keys; if {@code null} an empty {@link Map} will be used instead
         *
         * @exception NullPointerException if {@code uri} is {@code null}
         */
        private Uri {
            requireNonNull(uri, "uri");
            metadata = metadata == null || metadata.isEmpty() ? Map.of() : Map.copyOf(metadata);
        }

        @Override // DiscoveredUri (Object)
        public boolean equals(final Object other) {
            if (other == this) {
                return true;
            } else if (other != null && other.getClass() == this.getClass()) {
                return this.uri().equals(((Uri) other).uri());
            } else {
                return false;
            }
        }

        @Override // DiscoveredUri (Object)
        public int hashCode() {
            return this.uri().hashCode();
        }

        @Override // DiscoveredUri (Object)
        public String toString() {
            return
                this.metadata().isEmpty()
                ? this.uri().toString()
                : this.metadata().toString() + " " + this.uri().toString();
        }

    }

    /**
     * A {@linkplain FunctionalInterface functional interface} that transforms endpoint information as furnished by
     * Eureka into a {@link URI}.
     *
     * <p>Implementations of this method should return determinate values.</p>
     *
     * <p>Implementations of this method should be idempotent, but need not be safe for concurrent use by multiple
     * threads.</p>
     *
     * @see #uri(String, int, boolean, Map)
     */
    @FunctionalInterface
    private interface UriFactory {

        /**
         * Returns a non-{@code null} {@link Optional} housing a {@link URI} built from the supplied information, or an
         * {@linkplain Optional#empty() empty <code>Optional</code>} if the information was unsuitable.
         *
         * @param host a hostname; must not be {@code null}
         *
         * @param port a port number
         *
         * @param secure whether the port is designated <dfn>secure</dfn> in the semantics used by Eureka
         *
         * @param metadata a {@link Map} of {@link String}-typed metadata values indexed by {@link String}-typed
         * metadata keys; must not be {@code null}
         *
         * @return a non-{@code null} {@link Optional} housing a {@link URI} built from the supplied information, or an
         * {@linkplain Optional#empty() empty <code>Optional</code>} if the information was unsuitable
         *
         * @exception NullPointerException if {@code host} or {@code metadata} is {@code null}
         */
        Optional<URI> uri(String host, int port, boolean secure, Map<? extends String, ? extends String> metadata);

    }


}
