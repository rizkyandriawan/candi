package candi.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a page should be wrapped in a named layout.
 * The layout class is resolved by name (e.g. @Layout("base") → BaseLayout).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Layout {

    /**
     * The layout name (e.g. "base" resolves to BaseLayout).
     */
    String value();
}
