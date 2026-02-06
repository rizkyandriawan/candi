package candi.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Candi widget class (reusable UI component).
 * Used in .jhtml files: the Java class section uses @Widget.
 * Widgets are prototype-scoped — each {{ widget "name" }} call gets a fresh instance.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Widget {
}
