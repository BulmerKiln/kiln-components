/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.permissions;

/**
 * The principal the current request acts as, bound by authentication. Like the organization
 * context, reading it outside a scope fails: an unauthenticated request is bound as
 * {@link Principal.Anonymous} explicitly, never assumed.
 */
public final class CurrentPrincipal {

    private static final ScopedValue<Principal> CURRENT = ScopedValue.newInstance();

    private CurrentPrincipal() {}

    public static Principal get() {
        if (!CURRENT.isBound()) {
            throw new IllegalStateException("No principal in context");
        }
        return CURRENT.get();
    }

    public static <T, X extends Throwable> T call(Principal principal, ScopedValue.CallableOp<? extends T, X> op)
            throws X {
        if (CURRENT.isBound()) {
            throw new IllegalStateException("A principal is already bound; requests never switch principal");
        }
        return ScopedValue.where(CURRENT, principal).call(op);
    }

    public static void run(Principal principal, Runnable op) {
        call(principal, () -> {
            op.run();
            return null;
        });
    }
}
