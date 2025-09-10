package com.mcraft.api;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModInfo {
    String id();
    String name();
    String version() default "0.0.0";
    String author() default "";

    // added
    int apiVersion() default 1; // required API version for this mod
}
