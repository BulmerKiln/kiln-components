/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.lookup;

/**
 * An answer for every key, from wherever an assembly keeps it: a lookup never misses, so "not
 * found" is an answer of {@code V}'s own, such as an empty list or a safe default. Implementations
 * are named {@code *Lookup}, and the module that uses a lookup documents what its answers mean.
 *
 * @param <K> what's looked up
 * @param <V> the answer
 */
@FunctionalInterface
public interface Lookup<K, V> {

    V of(K key);
}
