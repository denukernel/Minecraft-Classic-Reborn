package net.classicremastered.toolkit.modeleditor;

import java.io.IOException;
import java.util.*;

/** Strict JSON syntax, independent of model fields. */
final class ModelJson {
    private final String text;
    private int position;
    private ModelJson(String text) { this.text = text; }

    static Object parse(String text) throws IOException {
        ModelJson parser = new ModelJson(text);
        Object result = parser.value(0);
        parser.space();
        if (parser.position != text.length()) throw parser.error("Trailing content");
        return result;
    }

    private IOException error(String message) { return new IOException(message + " at character " + position); }
    private void space() {
        while (position < text.length() && " \r\n\t".indexOf(text.charAt(position)) >= 0) position++;
    }
    private boolean take(char expected) {
        space();
        if (position < text.length() && text.charAt(position) == expected) { position++; return true; }
        return false;
    }
    private void expect(char expected) throws IOException { if (!take(expected)) throw error("Expected " + expected); }

    private Object value(int depth) throws IOException {
        if (depth > 128) throw error("Model nesting is too deep");
        space();
        if (position == text.length()) throw error("Unexpected end of JSON");
        if (text.charAt(position) == '"') return string();
        if (take('{')) {
            Map<String,Object> result = new LinkedHashMap<>();
            if (take('}')) return result;
            do {
                String key = string(); expect(':');
                if (result.containsKey(key)) throw error("Duplicate property " + key);
                result.put(key, value(depth + 1));
            } while (take(','));
            expect('}'); return result;
        }
        if (take('[')) {
            List<Object> result = new ArrayList<>();
            if (take(']')) return result;
            do { result.add(value(depth + 1)); } while (take(','));
            expect(']'); return result;
        }
        for (String literal : new String[] { "true", "false", "null" }) {
            if (text.startsWith(literal, position)) {
                position += literal.length();
                return literal.equals("null") ? null : Boolean.valueOf(literal);
            }
        }
        int start = position;
        if (position < text.length() && text.charAt(position) == '-') position++;
        if (position < text.length() && text.charAt(position) == '0') position++; else digits();
        if (position < text.length() && text.charAt(position) == '.') { position++; digits(); }
        if (position < text.length() && "eE".indexOf(text.charAt(position)) >= 0) {
            position++;
            if (position < text.length() && "+-".indexOf(text.charAt(position)) >= 0) position++;
            digits();
        }
        try {
            double number = Double.parseDouble(text.substring(start, position));
            if (!Double.isFinite(number)) throw error("Number must be finite");
            return number;
        } catch (NumberFormatException e) { throw error("Invalid number"); }
    }

    private void digits() throws IOException {
        int start = position;
        while (position < text.length() && text.charAt(position) >= '0' && text.charAt(position) <= '9') position++;
        if (start == position) throw error("Expected a digit");
    }
    private String string() throws IOException {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (position < text.length()) {
            char c = text.charAt(position++);
            if (c == '"') return result.toString();
            if (c < 32) throw error("Unescaped control character");
            if (c == '\\') {
                if (position == text.length()) throw error("Incomplete escape");
                c = text.charAt(position++);
                switch (c) {
                    case '"': case '\\': case '/': break;
                    case 'b': c = '\b'; break;
                    case 'f': c = '\f'; break;
                    case 'n': c = '\n'; break;
                    case 'r': c = '\r'; break;
                    case 't': c = '\t'; break;
                    case 'u':
                        if (position + 4 > text.length()) throw error("Incomplete Unicode escape");
                        try { c = (char) Integer.parseInt(text.substring(position, position + 4), 16); }
                        catch (NumberFormatException e) { throw error("Invalid Unicode escape"); }
                        position += 4; break;
                    default: throw error("Invalid escape");
                }
            }
            result.append(c);
        }
        throw error("Unterminated string");
    }

    static String write(Object value) throws IOException {
        if (value == null) return "null";
        if (value instanceof String text) {
            StringBuilder result = new StringBuilder("\"");
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '"': result.append("\\\""); break;
                    case '\\': result.append("\\\\"); break;
                    case '\n': result.append("\\n"); break;
                    case '\r': result.append("\\r"); break;
                    case '\t': result.append("\\t"); break;
                    default:
                        if (c < 32) result.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
                        else result.append(c);
                }
            }
            return result.append('"').toString();
        }
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Number number) {
            if (!Double.isFinite(number.doubleValue())) throw new IOException("Model numbers must be finite");
            return number.toString();
        }
        StringJoiner result;
        if (value instanceof Map<?,?> map) {
            result = new StringJoiner(",", "{", "}");
            for (var entry : map.entrySet()) result.add(write(entry.getKey()) + ":" + write(entry.getValue()));
        } else if (value instanceof List<?> list) {
            result = new StringJoiner(",", "[", "]");
            for (Object item : list) result.add(write(item));
        } else throw new IOException("Unsupported JSON value");
        return result.toString();
    }
}
