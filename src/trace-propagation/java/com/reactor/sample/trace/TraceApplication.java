package com.reactor.sample.trace;

import com.reactor.rust.annotations.ReactorApplication;
import com.reactor.rust.app.RestApplication;

@ReactorApplication(name = "Trace Propagation Sample", scanBasePackages = "com.reactor.sample.trace")
public final class TraceApplication {
    private TraceApplication() {}
    public static void main(String[] args) { RestApplication.run(TraceApplication.class, args); }
}
