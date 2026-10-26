/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.org;

/**
 * The organization the current work runs for. Every service method runs inside one, and reading
 * it outside one fails rather than defaulting, the same rule as {@code current_setting('app.org_id')}
 * without {@code missing_ok} (CORE-1).
 *
 * <p>The organization is bound with a {@link ScopedValue}, so it can't leak past the scope that set
 * it, and a scope can't be re-bound to a different organization. Jobs that cover several
 * organizations run one scope per organization, one after another.
 */
public final class OrgContext {

    private static final ScopedValue<Org> CURRENT = ScopedValue.newInstance();

    private OrgContext() {}

    /** Returns the current organization, or throws {@link IllegalStateException} outside a scope. */
    public static Org current() {
        if (!CURRENT.isBound()) {
            throw new IllegalStateException("No organization in context");
        }
        return CURRENT.get();
    }

    public static boolean isBound() {
        return CURRENT.isBound();
    }

    public static void run(Org org, Runnable op) {
        bind(org).run(op);
    }

    public static <T, X extends Throwable> T call(Org org, ScopedValue.CallableOp<? extends T, X> op) throws X {
        return bind(org).call(op);
    }

    private static ScopedValue.Carrier bind(Org org) {
        if (CURRENT.isBound() && !CURRENT.get().id().equals(org.id())) {
            throw new IllegalStateException("Already running for organization "
                    + CURRENT.get().slug() + "; refusing to switch to " + org.slug());
        }
        return ScopedValue.where(CURRENT, org);
    }
}
