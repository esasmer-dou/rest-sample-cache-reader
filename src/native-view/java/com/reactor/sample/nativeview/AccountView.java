package com.reactor.sample.nativeview;

import com.reactor.rust.view.NativeView;
import com.reactor.rust.view.Utf8Slice;
import com.reactor.rust.view.ViewField;
import com.reactor.sample.model.account.AccountState;

@NativeView
public interface AccountView {
    @ViewField(index = 0) long id();
    @ViewField(index = 1) Utf8Slice code();
    @ViewField(index = 2) AccountState status();
    @ViewField(index = 3) long balanceCents();
    @ViewField(index = 4) boolean active();
}
