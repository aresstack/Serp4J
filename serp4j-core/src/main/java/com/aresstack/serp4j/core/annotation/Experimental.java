package com.aresstack.serp4j.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks API that may still change incompatibly in a minor release of the 0.x line (ADR-013). Everything not
 * annotated follows the normal compatibility rules of the module it belongs to.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE})
public @interface Experimental {

    /** Why the API is experimental, or what has to happen before it is stabilised. */
    String value() default "";
}
