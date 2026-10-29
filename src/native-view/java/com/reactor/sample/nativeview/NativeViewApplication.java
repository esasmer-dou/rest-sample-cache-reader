package com.reactor.sample.nativeview;

import com.reactor.rust.annotations.ReactorApplication;
import com.reactor.rust.app.RestApplication;

@ReactorApplication(name = "Native Account View", scanBasePackages = "com.reactor.sample.nativeview")
public final class NativeViewApplication {
    private NativeViewApplication() {}
    public static void main(String[] args) { RestApplication.run(NativeViewApplication.class, args); }
}
