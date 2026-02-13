package candi.runtime;

import java.util.List;
import java.util.Map;

/**
 * Runtime helper methods for the Candi template engine.
 */
public class CandiRuntime {

    /**
     * Index access for List, Map, and arrays.
     * Used by the generated code for {{ items[0] }} and {{ map["key"] }} expressions.
     */
    public static Object index(Object collection, Object key) {
        if (collection == null) return null;
        if (collection instanceof List<?> list) {
            return list.get(((Number) key).intValue());
        }
        if (collection instanceof Map<?, ?> map) {
            return map.get(key);
        }
        if (collection.getClass().isArray()) {
            return java.lang.reflect.Array.get(collection, ((Number) key).intValue());
        }
        throw new IllegalArgumentException("Cannot index into " + collection.getClass().getName());
    }
}
