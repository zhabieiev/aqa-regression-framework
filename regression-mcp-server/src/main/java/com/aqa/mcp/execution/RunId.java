package com.aqa.mcp.execution;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

final class RunId {
    private static final Pattern FORMAT = Pattern.compile("run-[0-9a-f]{32}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private RunId() { }
    static String generate() { byte[] value = new byte[16]; RANDOM.nextBytes(value); return "run-" + HexFormat.of().formatHex(value); }
    static boolean valid(String value) { return value != null && FORMAT.matcher(value).matches(); }
}
