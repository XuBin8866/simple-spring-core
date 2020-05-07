package com.xxbb.framework.annotation;

import java.lang.annotation.*;

/**
 * @author Administrator
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoWired {
    String value() default "";
}
