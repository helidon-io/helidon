/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.secrets.mp.configsource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.secrets.Secrets;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.model.SecretBundleContentDetails;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * An {@link AutoCloseable} {@link ConfigSource} that retrieves configuration property values from an <a
 * href="https://docs.oracle.com/en-us/iaas/Content/KeyManagement/Concepts/keyoverview.htm" target="_top">Oracle Cloud
 * Infrastructure Vault</a>.
 *
 * <p>Although not {@code abstract}, this class is normally used as a superclass since it (deliberately) lacks a
 * zero-argument {@code public} constructor, and so hence cannot be used as a Java {@linkplain java.util.ServiceLoader
 * service provider}.</p>
 */
@SuppressWarnings("try")
class AbstractSecretBundleConfigSource implements AutoCloseable, ConfigSource {


    /*
     * Static fields.
     */


    private static final int NOT_FOUND = 404;


    /*
     * Instance fields.
     */


    private final Function<? super String, ? extends String> base64Decoder;

    private final Function<? super String, ? extends SecretBundleContentDetails> f;

    private final Supplier<? extends AutoCloseable> closeableSupplier;


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link AbstractSecretBundleConfigSource}.
     *
     * @param f a {@link Function} that accepts a configuration property name (a {@link String}) and returns a {@link
     * SecretBundleContentDetails} object representing the value; must not be {@code null}; must be safe for concurrent
     * use by multiple threads
     *
     * @param closeableSupplier a {@link Supplier} of an {@link AutoCloseable} typically supplying the {@link Secrets}
     * typically used by the supplied {@link Function}; must not be {@code null}; its {@link Supplier#get()} method will
     * be invoked when the {@link #close()} method is invoked, and {@link AutoCloseable#close()} will be invoked on its
     * return value if the return value is non-{@code null}
     *
     * @see #AbstractSecretBundleConfigSource(Function, Supplier, Function)
     */
    AbstractSecretBundleConfigSource(Function<? super String, ? extends SecretBundleContentDetails> f,
                                     Supplier<? extends AutoCloseable> closeableSupplier) {
        this(f, closeableSupplier, null);
    }

    /**
     * Creates a new {@link AbstractSecretBundleConfigSource}.
     *
     * @param f a {@link Function} that accepts a configuration property name (a {@link String}) and returns a {@link
     * SecretBundleContentDetails} object representing the value; must not be {@code null}; must be safe for concurrent
     * use by multiple threads
     *
     * @param closeableSupplier a {@link Supplier} of an {@link AutoCloseable} typically supplying the {@link Secrets}
     * typically used by the supplied {@link Function}; must not be {@code null}; its {@link Supplier#get()} method will
     * be invoked when the {@link #close()} method is invoked, and {@link AutoCloseable#close()} will be invoked on its
     * return value if the return value is non-{@code null}
     *
     * @param base64Decoder a {@link Function} that accepts a Base64-encoded {@link String} to decode and decodes it,
     * returning the result; may be {@code null} in which case an implementation based on {@link Base64} will be used
     * instead; if non-{@code null}, must be safe for concurrent use by multiple threads
     */
    AbstractSecretBundleConfigSource(Function<? super String, ? extends SecretBundleContentDetails> f,
                                     Supplier<? extends AutoCloseable> closeableSupplier,
                                     Function<? super String, ? extends String> base64Decoder) {
        super();
        this.f = Objects.requireNonNull(f, "f");
        this.closeableSupplier = Objects.requireNonNull(closeableSupplier, "closeableSupplier");
        if (base64Decoder == null) {
            this.base64Decoder = s -> s == null ? null : new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
        } else {
            this.base64Decoder = base64Decoder;
        }
    }


    /*
     * Instance methods.
     */


    /**
     * Closes this {@link AbstractSecretBundleConfigSource}.
     *
     * <p>During an invocation of this method, the {@link Supplier#get()} method of the {@link Supplier} of {@link
     * AutoCloseable} instances supplied at construction time will be invoked, and {@link AutoCloseable#close()} will be
     * invoked on its return value, if the return value is non-{@code null}.</p>
     *
     * <p>This method is, and overrides of this method must be, safe for concurrent use by multiple threads.</p>
     *
     * @see #AbstractSecretBundleConfigSource(Function, Supplier, Function)
     *
     * @see AutoCloseable#close()
     */
    @Override // AutoCloseable
    public void close() {
        AutoCloseable ac = this.closeableSupplier.get();
        if (ac != null) {
            try {
                ac.close();
            } catch (RuntimeException runtimeException) {
                throw runtimeException;
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException(exception.getMessage(), exception);
            }
        }
    }

    /**
     * Returns a name for this {@link AbstractSecretBundleConfigSource}.
     *
     * <p>The default implementation of this method returns a valud as if computed by {@link Class#getName()
     * this.getClass().getName()}.</p>
     *
     * <p>This method does not, and overrides of this method must not, return {@code null}.</p>
     *
     * <p>This method is, and overrides of this method must be, safe for concurrent use by multiple threads.</p>
     *
     * @return a non-{@code null} name
     *
     * @see ConfigSource#getName()
     */
    @Override // ConfigSource
    public String getName() {
        return this.getClass().getName();
    }

    /**
     * Returns a {@link Map} representing a subset of properties which this {@link AbstractSecretBundleConfigSource} may
     * be capable of reproducing, or not.
     *
     * <p>This area of the specification permits any return value.  Consequently the default implementation of this method
     * returns a value equal to that returned by an invocation of {@link Map#of()}.</p>
     *
     * <p>Subclasses may feel free to override this method to do almost anything.</p>
     *
     * <p>This method does not, and overrides of this method must not, return {@code null}.</p>
     *
     * <p>This method is, and overrides of this method must be, safe for concurrent use by multiple threads.</p>
     *
     * @return a non-{@code null}, immutable {@link Map}
     */
    @Override // ConfigSource
    public Map<String, String> getProperties() {
        return Map.of();
    }

    /**
     * Returns a {@link Set} representing a subset of the property names for which this {@link
     * AbstractSecretBundleConfigSource} may or may not be capable of locating values.
     *
     * <p>This area of the specification permits any return value. Consequently the default implementation of this method
     * returns a value equal to that returned by an invocation of {@link Set#of()}.</p>
     *
     * <p>Subclasses may feel free to override this method to do almost anything.</p>
     *
     * <p>This method does not, and overrides of this method must not, return {@code null}.</p>
     *
     * <p>This method is, and overrides of this method must be, safe for concurrent use by multiple threads.</p>
     *
     * @return a non-{@code null}, immutable {@link Set}
     */
    @Override // ConfigSource
    public Set<String> getPropertyNames() {
        return Set.of();
    }

    /**
     * Returns a value for the supplied {@code propertyName}, or {@code null} if there is no such value known at the
     * moment of invocation.
     *
     * <p>This method is safe for concurrent use by multiple threads.</p>
     *
     * @param propertyName the name of the property; may be {@code null}
     *
     * @return a suitable value, or {@code null} if there is no value suitable for the supplied {@code propertyName}
     */
    @Override // ConfigSource
    public String getValue(String propertyName) {
        try {
            return
                this.f.apply(propertyName) instanceof Base64SecretBundleContentDetails b64
                ? this.base64Decoder.apply(b64.getContent())
                : null;
        } catch (BmcException e) {
            if (e.getStatusCode() == NOT_FOUND) {
                // The MicroProfile Config specification dictates that we return null in this case.
                return null;
            }
            throw e;
        }
    }

}
