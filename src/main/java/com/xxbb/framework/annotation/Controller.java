package com.xxbb.framework.annotation;

import java.lang.annotation.*;

/**
 * @author xxbb
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Controller {
    String value() default "";
}
