package candi.devtools;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parent-last ClassLoader for hot-reloaded page classes.
 *
 * Page classes loaded by this classloader still implement CandiPage
 * (from the parent classloader), so they work seamlessly with Spring DI.
 * Only page classes are loaded child-first; everything else delegates to parent.
 */
public class PageClassLoader extends ClassLoader {

    private final Map<String, byte[]> classes = new ConcurrentHashMap<>();

    public PageClassLoader(ClassLoader parent) {
        super(parent);
    }

    /**
     * Add a class to be loaded by this classloader.
     */
    public void addClass(String name, byte[] bytecode) {
        classes.put(name, bytecode);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Parent-last for our page classes only
        byte[] bytecode = classes.get(name);
        if (bytecode != null) {
            Class<?> clazz = defineClass(name, bytecode, 0, bytecode.length);
            if (resolve) {
                resolveClass(clazz);
            }
            return clazz;
        }
        // Everything else: delegate to parent
        return super.loadClass(name, resolve);
    }

    /**
     * Check if this classloader has a specific class loaded.
     */
    public boolean hasClass(String name) {
        return classes.containsKey(name);
    }
}
