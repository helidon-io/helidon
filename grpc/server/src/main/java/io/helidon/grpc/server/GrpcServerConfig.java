package io.helidon.grpc.server;


import io.helidon.config.Config;
import java.io.Serializable;


/**
 * Configuration class for the {@link GrpcServer} implementations.
 */
public class GrpcServerConfig
        implements Serializable
    {
    // ---- constructors ------------------------------------------------

    /**
     * Default constructor for serialization.
     */
    public GrpcServerConfig()
        {
        }

    /**
     * Construct {@link GrpcServerConfig} instance with native transport and TLS disabled.
     *
     * @param name  the server name
     * @param port  the port to listen on
     */
    GrpcServerConfig(String name, int port)
        {
        this(name, port, false, false, null, null, null);
        }

    /**
     * Construct {@link GrpcServerConfig} instance.
     *
     * @param name            the server name
     * @param port            the port to listen on
     * @param nativeTransport {@code true} to enable native transport for
     *                        the server
     * @param tls             {@code true} to enable TLS for the server
     * @param tlsCert         the location of the TLS certificate file
     *                        (required if tls is enabled)
     * @param tlsKey          the location of the TLS key file (required if
     *                        tls is enabled)
     * @param tlsCaCert       the location of the optional TLS CA cert file
     */
    public GrpcServerConfig(String name, int port, boolean nativeTransport, boolean tls, String tlsCert, String tlsKey, String tlsCaCert)
        {
        this.name = name;
        this.port = port;
        this.nativeTransport = nativeTransport;
        this.tls = tls;
        this.tlsCert = tlsCert;
        this.tlsKey = tlsKey;
        this.tlsCaCert = tlsCaCert;
        }

    static GrpcServerConfig defaultConfig()
        {
        return new GrpcServerConfig(DEFAULT_NAME, DEFAULT_PORT);
        }

    /**
     * Creates new instance with defaults from external configuration source.
     *
     * @param config the externalized configuration
     * @return a new instance
     */
    static GrpcServerConfig create(Config config) {
        String name = config.get("name").asString().orElse(DEFAULT_NAME);
        int    port = config.get("port").asInt().orElse(DEFAULT_PORT);

        return new GrpcServerConfig(name, port);
    }

    // ---- accessors ---------------------------------------------------

    /**
     * Get the server name.
     *
     * @return the server name
     */
    public String name()
        {
        return name;
        }

    /**
     * Get the server port.
     *
     * @return the server port
     */
    public int port()
        {
        return port;
        }

    /**
     * Determine whether use native transport if possible.
     * <p/>
     * If native transport support is enabled, gRPC server will use epoll on
     * Linux, or kqueue on OS X. Otherwise, the standard NIO transport will
     * be used.
     *
     * @return {@code true} if native transport should be used
     */
    public boolean useNativeTransport()
        {
        return nativeTransport;
        }

    /**
     * Determine whether TLS is enabled.
     *
     * @return {@code true} if TLS is enabled
     */
    public boolean isTLS()
        {
        return tls;
        }

    /**
     * Obtain the location of the TLS certs file to use.
     *
     * @return the location of the TLS certs file to use
     */
    public String tlsCert()
        {
        return tlsCert;
        }

    /**
     * Obtain the location of the TLS key file to use.
     *
     * @return the location of the TLS key file to use
     */
    public String tlsKey()
        {
        return tlsKey;
        }

    /**
     * Obtain the location of the TLS CA certs file to use.
     *
     * @return the location of the TLS CA certs file to use
     */
    public String tlsCaCert()
        {
        return tlsCaCert;
        }

    // ---- constants -------------------------------------------------------

    /**
     * The default server name.
     */
    public static final String DEFAULT_NAME = "grpc.server";

    /**
     * The default grpc port.
     */
    public static final int DEFAULT_PORT = 1408;

    // ---- data members ------------------------------------------------

    private String name;

    private int port;

    private boolean nativeTransport;

    private boolean tls;

    private String tlsCert;

    private String tlsKey;

    private String tlsCaCert;
    }
