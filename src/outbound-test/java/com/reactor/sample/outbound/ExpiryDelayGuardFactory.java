package com.reactor.sample.outbound;

import com.reactor.rust.bridge.RequestGuard;
import com.reactor.rust.bridge.RequestGuardFactory;
import java.lang.reflect.Method;

/** Test classpath only: the token expires after Rust verification, before Java authorization. */
public final class ExpiryDelayGuardFactory implements RequestGuardFactory {
    @Override public int order() { return 9; }
    @Override public RequestGuard create(Class<?> owner, Method method) {
        if (!Boolean.getBoolean("test.edge.expire-in-queue") || !method.getName().equals("me")) return null;
        return request -> {
            try { Thread.sleep(2500); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        };
    }
}
