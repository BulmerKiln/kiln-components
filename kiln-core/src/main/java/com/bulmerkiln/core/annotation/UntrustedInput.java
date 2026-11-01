/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that consumes untrusted input and therefore requires a fuzz target (BUILD-14).
 * The build-enforced {@code fuzzCheck} task finds every annotated method and fails when no
 * {@code @FuzzTest} calls it.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface UntrustedInput {}
