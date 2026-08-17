package com.aqa.mcp.execution;

import java.util.Locale;
import java.util.Map;

/** Deterministic conservative sanitizer for diagnostic strings that cross the public MCP boundary. */
final class PublicDiagnosticSanitizer {
    private PublicDiagnosticSanitizer() { }
    static String bound(String value, int maximum, boolean[] truncated) {
        if (value == null || value.isEmpty() || maximum <= 0) { if (value != null && !value.isEmpty()) truncated[0] = true; return ""; }
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset); offset += Character.charCount(codePoint);
            if (codePoint == 0xfffd || Character.isSurrogate((char) codePoint)) { result.append("[invalid-text]"); truncated[0] = true; }
            else if (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\r' && codePoint != '\t') { result.append(' '); truncated[0] = true; }
            else result.appendCodePoint(codePoint);
            if (result.length() >= maximum) { truncated[0] = true; break; }
        }
        String clean = redactSecrets(result.substring(0, Math.min(result.length(), maximum)));
        clean = clean.replaceAll("(?i)(?:file:/*)?(?:[a-z]:[\\\\/]|/)[^\\s\\]\\[()\"']+", "[redacted-path]");
        if (clean.length() > maximum) { truncated[0] = true; clean = clean.substring(0, maximum); }
        return clean;
    }
    private static String redactSecrets(String input) {
        String result = input;
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT); String value = entry.getValue();
            if (value != null && value.length() >= 4 && (key.contains("token") || key.contains("secret") || key.contains("password") || key.contains("credential") || key.matches(".*(^|_)key($|_).*"))) result = result.replace(value, "[redacted]");
        }
        return result;
    }
}
