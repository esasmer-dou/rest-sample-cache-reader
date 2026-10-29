package com.reactor.sample.jwt;

import com.reactor.rust.annotations.ReactorApplication;
import com.reactor.rust.app.RestApplication;

@ReactorApplication(name = "Local public-key JWT sample", scanBasePackages = "com.reactor.sample.jwt")
public final class JwtApplication {
    private JwtApplication() {}
    public static void main(String[] args) { RestApplication.run(JwtApplication.class, args); }
}
