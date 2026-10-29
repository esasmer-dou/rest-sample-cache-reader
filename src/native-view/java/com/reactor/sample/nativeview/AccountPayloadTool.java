package com.reactor.sample.nativeview;

import com.reactor.rust.view.Utf8Slice;
import com.reactor.sample.model.account.AccountState;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Offline producer example using exactly the generated schema writer, not handwritten offsets. */
public final class AccountPayloadTool {
    private AccountPayloadTool() {}
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 3) throw new IllegalArgumentException("Usage: output.bin [code] [repeat:1..40]");
        int repeat = args.length == 3 ? Integer.parseInt(args[2]) : 1;
        if (repeat < 1 || repeat > 40) throw new IllegalArgumentException("repeat must be 1..40");
        byte[] code = (args.length > 1 ? args[1] : "ACC-TR-42").repeat(repeat).getBytes(StandardCharsets.UTF_8);
        if (code.length < 1 || code.length > 1024) throw new IllegalArgumentException("code must contain 1..1024 UTF-8 bytes");
        var output = ByteBuffer.allocate(AccountView_NativeWriter.HEADER_SIZE + code.length);
        try (var text = new Utf8Slice().reset(ByteBuffer.wrap(code), 0, code.length)) {
            AccountView_NativeWriter.write(output, 0, 42, text, AccountState.OPEN, 123456, true);
        }
        Files.write(Path.of(args[0]), output.array());
    }
}
