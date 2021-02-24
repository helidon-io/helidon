package io.helidon.lra;

public class AQChannelConfig {
    String owner = "ORDERUSER", type, destination; //todo remove owner if possible

    public String toString() {
        return "owner:" + owner + " type:" + type + " destination:" + destination;
    }
}
