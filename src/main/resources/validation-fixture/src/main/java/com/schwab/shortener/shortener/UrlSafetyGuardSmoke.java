package com.schwab.shortener.shortener;

/**
 * Fast smoke runner used by the isolated validation loop (no Spring/JUnit required).
 */
public final class UrlSafetyGuardSmoke {

    private UrlSafetyGuardSmoke() {
    }

    public static void main(String[] args) {
        UrlSafetyGuard guard = new UrlSafetyGuard();
        guard.validatePublicHttpUrl("https://example.com/ok");
        try {
            guard.validatePublicHttpUrl("javascript:alert(1)");
            fail("rejectsJavascript expected failure");
        } catch (IllegalArgumentException ignored) {
            // expected
        }
        try {
            guard.validatePublicHttpUrl("http://127.0.0.1/secret");
            fail("rejectsLoopback expected Private/loopback rejection for 127.0.0.1");
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (!(message.contains("private") || message.contains("loop") || message.contains("metadata"))) {
                fail("rejectsLoopback assertion failed: " + ex.getMessage());
            }
        }
        System.out.println("Tests run: 3, Failures: 0");
    }

    private static void fail(String message) {
        System.out.println("Tests run: 3, Failures: 1");
        System.out.println("FAILURES:");
        System.out.println("rejectsLoopback: " + message);
        throw new IllegalStateException(message);
    }
}
