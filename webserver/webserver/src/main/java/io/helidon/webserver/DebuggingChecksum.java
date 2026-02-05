package io.helidon.webserver;

import java.util.zip.Checksum;

public final class DebuggingChecksum implements Checksum {
    private final String prefix;
    private final Checksum inner;
    private int index;

    public DebuggingChecksum(final String prefix, final Checksum inner) {
        this.prefix = prefix;
        this.inner = inner;
    }

    @Override
    public void update(final int b) {
        inner.update(b);
        System.out.printf("%s [%3d] 0x%02X -> 0x%016X%n", prefix, index++, b & 0xFF, inner.getValue());
    }

    @Override
    public void update(final byte[] b, final int off, final int len) {
        for (int i = 0; i < len; i++) {
            update(b[off + i]);
        }
    }

    @Override
    public long getValue() {
        return inner.getValue();
    }

    @Override
    public void reset() {
        inner.reset();
    }

    @Override
    public String toString() {
        return String.format("DebuggingChecksum(0x%08X)", getValue());
    }
}
