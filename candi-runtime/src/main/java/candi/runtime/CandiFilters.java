package candi.runtime;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;

/**
 * Built-in filter functions for the Candi template engine.
 * Used via the pipe syntax: {{ name | upper }}
 */
public class CandiFilters {

    public static String upper(Object val) {
        return String.valueOf(val).toUpperCase();
    }

    public static String lower(Object val) {
        return String.valueOf(val).toLowerCase();
    }

    public static String capitalize(Object val) {
        String s = String.valueOf(val);
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String trim(Object val) {
        return String.valueOf(val).trim();
    }

    public static int length(Object val) {
        if (val instanceof String s) return s.length();
        if (val instanceof Collection<?> c) return c.size();
        if (val != null && val.getClass().isArray()) return java.lang.reflect.Array.getLength(val);
        return String.valueOf(val).length();
    }

    public static String escape(Object val) {
        String s = String.valueOf(val);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#x27;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String truncate(Object val, int maxLen) {
        String s = String.valueOf(val);
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }

    public static String replace(Object val, String from, String to) {
        return String.valueOf(val).replace(from, to);
    }

    public static String date(Object val, String format) {
        if (val instanceof TemporalAccessor ta) {
            return DateTimeFormatter.ofPattern(format).format(ta);
        }
        if (val instanceof String s && !s.isBlank()) {
            try {
                String cleaned = s.trim();
                if (cleaned.endsWith("Z")) cleaned = cleaned.substring(0, cleaned.length() - 1);
                if (cleaned.length() > 19 && (cleaned.charAt(19) == '+' || cleaned.charAt(19) == '-'))
                    cleaned = cleaned.substring(0, 19);
                LocalDateTime dt = LocalDateTime.parse(cleaned);
                return DateTimeFormatter.ofPattern(format).format(dt);
            } catch (Exception e1) {
                try {
                    return DateTimeFormatter.ofPattern(format).format(LocalDate.parse(s.trim()));
                } catch (Exception e2) { return s; }
            }
        }
        return val == null ? "" : String.valueOf(val);
    }

    public static String number(Object val, String format) {
        if (val instanceof Number n) {
            return new DecimalFormat(format).format(n);
        }
        return String.valueOf(val);
    }

    public static String join(Object val, String separator) {
        if (val instanceof Collection<?> c) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Object item : c) {
                if (!first) sb.append(separator);
                sb.append(String.valueOf(item));
                first = false;
            }
            return sb.toString();
        }
        return String.valueOf(val);
    }

    public static Object defaultVal(Object val, Object def) {
        return val != null ? val : def;
    }
}
