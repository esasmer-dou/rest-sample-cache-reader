package com.reactor.sample.slab;

import com.reactor.rust.annotations.ReactorApplication;
import com.reactor.rust.app.RestApplication;

@ReactorApplication(name = "Shared Slab Cache Sample", scanBasePackages = "com.reactor.sample.slab")
public final class SlabApplication {
    private SlabApplication() {}
    public static void main(String[] args) { RestApplication.run(SlabApplication.class, args); }
}
