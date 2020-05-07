package com.xxbb.framework.annotation;

import java.lang.annotation.*;

/**
 * @author xxbb
 */
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestMapping {
    String value() default "";
}
