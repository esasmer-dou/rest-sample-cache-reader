package com.reactor.sample.nativeview;

import com.reactor.rust.exception.BadRequestException;
import com.reactor.rust.view.Utf8Slice;
import com.reactor.sample.model.account.AccountState;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountHandlerTest {
    static ByteBuffer record(String code) {
        byte[] utf8 = code.getBytes(StandardCharsets.UTF_8);
        var out = ByteBuffer.allocateDirect(33 + utf8.length);
        try (var text = new Utf8Slice().reset(ByteBuffer.wrap(utf8), 0, utf8.length)) {
            assertEquals(out.capacity(), AccountView_NativeWriter.write(out, 0, 42, text, AccountState.OPEN, 123456, true));
        }
        return out;
    }

    @Test void overflowRetryAndExceptionsAlwaysEndTheNativeBorrow() {
        var handler = new AccountHandler();
        var input = record("\u0130pek \ud83d\ude80\n\"acc\"");
        var tiny = ByteBuffer.allocateDirect(8);
        assertTrue(handler.view(tiny, 0, input, input.capacity(), "", "", "") < 0);
        try (var borrowed = AccountView_NativeView.threadLocalView(input, 0, input.capacity())) {
            assertEquals(42, borrowed.id());
        }
        var viewOut = ByteBuffer.allocateDirect(4096);
        var dtoOut = ByteBuffer.allocateDirect(4096);
        int a = handler.view(viewOut, 5, input, input.capacity(), "", "", "");
        int b = handler.dto(dtoOut, 5, input, input.capacity(), "", "", "");
        byte[] expected = new byte[a], actual = new byte[b];
        viewOut.get(5, expected); dtoOut.get(5, actual);
        // Unicode and short newline escapes represent the same character in this known fixture.
        assertEquals(new String(expected, StandardCharsets.UTF_8).replace("\\u000a", "\\n"),
                new String(actual, StandardCharsets.UTF_8));
        assertEquals(0, viewOut.position());
        input.put(16, (byte) 99);
        assertThrows(BadRequestException.class, () -> handler.view(viewOut, 0, input, input.capacity(), "", "", ""));
        input.put(16, (byte) 0);
        assertTrue(handler.view(viewOut, 0, input, input.capacity(), "", "", "") > 0);
    }

    @Test void closedViewsCannotReadReusedRequestMemoryAndThreadsKeepIndependentBorrows() throws Exception {
        var input = record("ACC-TR-42");
        var closed = AccountView_NativeView.threadLocalView(input, 0, input.capacity());
        closed.close();
        input.putLong(0, 99);
        assertThrows(IllegalStateException.class, closed::id);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 16; i++) tasks.add(pool.submit(() -> {
                var handler = new AccountHandler();
                var body = record("ACC-TR-42");
                var out = ByteBuffer.allocateDirect(1024);
                for (int n = 0; n < 100; n++) assertTrue(handler.view(out, 0, body, body.capacity(), "", "", "") > 0);
            }));
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
    }
}
