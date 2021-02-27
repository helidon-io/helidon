package io.helidon.lra;

public class AQChannelConfig {
    String owner, type, destination;

    public String toString() {
        return "owner:" + owner + " type:" + type + " destination:" + destination;
    }
}
