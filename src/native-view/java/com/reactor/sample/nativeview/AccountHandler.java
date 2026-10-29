package com.reactor.sample.nativeview;

import com.reactor.rust.annotations.PostMapping;
import com.reactor.rust.annotations.GetMapping;
import com.reactor.rust.annotations.RawRequestData;
import com.reactor.rust.annotations.RouteWorkload;
import com.reactor.rust.annotations.RestController;
import com.reactor.rust.exception.BadRequestException;
import com.reactor.rust.http.RawResponse;
import com.reactor.rust.json.JsonBufferWriter;
import com.reactor.sample.model.account.AccountSnapshot;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

/** The synchronous JNI invocation owns input storage. Neither route retains a borrowed view. */
@RestController("/api/v1/accounts")
@RouteWorkload(value = RouteWorkload.Type.STANDARD, budget = "account-read")
public final class AccountHandler {
    private static final RawResponse HEALTH = RawResponse.json("{\"status\":\"UP\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    @GetMapping("/health")
    public RawResponse health() { return HEALTH; }

    @PostMapping("/view")
    @RawRequestData
    public int view(ByteBuffer out, int offset, ByteBuffer body, int length,
                    String path, String query, String headers) {
        try (var account = borrow(body, length)) {
            int position = out.position();
            try {
                out.position(offset);
                return AccountView_NativeWriter.writeJson(account, out);
            } catch (BufferOverflowException overflow) {
                // Pure projection: discard partial output and retry from the same immutable request.
                return -(256 + 6 * account.code().byteLength());
            } finally { out.position(position); }
        }
    }

    /** Explicit comparison: adds a record, String and payload array; no reflection serializer. */
    @PostMapping("/dto")
    @RawRequestData
    public int dto(ByteBuffer out, int offset, ByteBuffer body, int length,
                   String path, String query, String headers) {
        try (var account = borrow(body, length)) {
            var dto = new AccountSnapshot(account.id(), account.code().materializeString(), account.status(),
                    account.balanceCents(), account.active());
            return JsonBufferWriter.reusable(out, offset).beginObject()
                    .fieldLong("id", dto.id()).comma()
                    .fieldString("code", dto.code()).comma()
                    .fieldString("status", dto.status().name()).comma()
                    .fieldLong("balanceCents", dto.balanceCents()).comma()
                    .fieldBoolean("active", dto.active()).endObject().result();
        }
    }

    private static AccountView_NativeView borrow(ByteBuffer body, int length) {
        if (body == null || !body.isDirect() || length < AccountView_NativeView.HEADER_SIZE || length > 4096) {
            throw new BadRequestException("Expected one schema-1 account record, at most 4096 bytes");
        }
        AccountView_NativeView view;
        try { view = AccountView_NativeView.threadLocalView(body, 0, length); }
        catch (IllegalArgumentException failure) { throw new BadRequestException("Invalid account binary layout", failure); }
        if (view.id() < 1 || view.code().byteLength() == 0 || view.code().byteLength() > 1024) {
            view.close();
            throw new BadRequestException("Account id must be positive; code must contain 1..1024 UTF-8 bytes");
        }
        return view;
    }
}
