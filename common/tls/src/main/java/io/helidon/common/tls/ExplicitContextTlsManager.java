package io.helidon.common.tls;

import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

class ExplicitContextTlsManager implements TlsManager {
    private static final String TYPE = "explicit";
    private final SSLContext sslContext;

    ExplicitContextTlsManager(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    @Override
    public void init(TlsConfig tls) {
    }

    @Override
    public void reload(TlsMaterial tls) {
        throw new UnsupportedOperationException(
                "TLS cannot be reloaded when an explicit instance of SSL context was used to create it");
    }

    @Override
    public SSLContext sslContext() {
        return sslContext;
    }

    @Override
    public Optional<X509KeyManager> keyManager() {
        return Optional.empty();
    }

    @Override
    public Optional<X509TrustManager> trustManager() {
        return Optional.empty();
    }

    @Override
    public String name() {
        return TYPE;
    }

    @Override
    public String type() {
        return TYPE;
    }
}
