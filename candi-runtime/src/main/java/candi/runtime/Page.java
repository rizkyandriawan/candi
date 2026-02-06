package candi.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Candi page class and declares its URL path.
 * Used in .jhtml files: the Java class section uses @Page("/path").
 * The compiler reads this annotation to generate @CandiRoute metadata.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Page {

    /**
     * The URL path pattern (e.g. "/posts", "/post/{id}/edit").
     */
    String value();

    /**
     * The layout name to wrap this page in (e.g. "base" resolves to BaseLayout).
     * Empty string means no layout.
     */
    String layout() default "";
}
