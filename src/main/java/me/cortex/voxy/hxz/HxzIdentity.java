package me.cortex.voxy.hxz;

import java.util.function.Supplier;

/** Keeps client classes out of the server's Level mixin. */
public final class HxzIdentity {
    public static Supplier<String> clientNamespace = () -> "";
    private HxzIdentity() {}
}
