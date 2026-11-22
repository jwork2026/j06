package com.gameengine.recording;

import java.util.ArrayList;
import java.util.List;

public final class RecordingJson {
    private RecordingJson() {}

    public static String field(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        int c = json.indexOf(':', i);
        if (c < 0) return null;
        int start = c + 1, depth = 0;
        boolean quoted = false, escape = false;
        for (int j = start; j < json.length(); j++) {
            char ch = json.charAt(j);
            if (quoted) {
                if (escape) escape = false;
                else if (ch == '\\') escape = true;
                else if (ch == '"') quoted = false;
            } else {
                if (ch == '"') quoted = true;
                else if (ch == '[' || ch == '{') depth++;
                else if (ch == ',' && depth == 0 || ch == '}' && depth == 0)
                    return json.substring(start, j).trim();
                else if (ch == ']' || ch == '}') depth--;
            }
        }
        return json.substring(start).trim();
    }

    public static String stripQuotes(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length()-1);
        }
        return s;
    }

    public static double parseDouble(String s) {
        if (s == null) return 0.0;
        try { return Double.parseDouble(stripQuotes(s)); } catch (Exception e) { return 0.0; }
    }

    public static String[] splitTopLevel(String arr) {
        List<String> out = new ArrayList<>();
        int depth = 0; int start = 0;
        for (int i = 0; i < arr.length(); i++) {
            char ch = arr.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}') depth--;
            else if (ch == ',' && depth == 0) {
                out.add(arr.substring(start, i));
                start = i + 1;
            }
        }
        if (start < arr.length()) out.add(arr.substring(start));
        return out.stream().map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    public static String extractArray(String json, int startIdx) {
        int i = startIdx;
        if (i >= json.length() || json.charAt(i) != '[') return "";
        int depth = 1;
        int begin = i + 1;
        i++;
        for (; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(begin, i);
                }
            }
        }
        return "";
    }
}


