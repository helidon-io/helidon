package io.helidon.tests.integration.packaging.grpc;

import javax.annotation.Generated;

import io.grpc.Channel;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.out.println(Channel.class.getName() + " " + Generated.class.getName());
    }
}
