package com.reactor.sample.outbound;

import com.reactor.rust.annotations.ReactorApplication;
import com.reactor.rust.app.RestApplication;
import com.reactor.rust.config.PropertiesLoader;

@ReactorApplication(name = "Outbound and Edge Authentication Sample", scanBasePackages = "com.reactor.sample.outbound")
public final class OutboundApplication {
    private OutboundApplication() {}
    public static void main(String[] args) {
        PropertiesLoader.load();
        if (PropertiesLoader.getBoolean("sample.edge.jwt.enabled", false)) AuthHandler_EdgeJwtPolicies.install();
        RestApplication.run(OutboundApplication.class, args);
    }
}
