/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.core.organization;

/**
 * The organization the current work runs for. Every service method runs inside one, and reading
 * it outside one fails rather than defaulting, the same rule as {@code current_setting('app.organization_id')}
 * without {@code missing_ok} (CORE-1).
 *
 * <p>The organization is bound with a {@link ScopedValue}, so it can't leak past the scope that set
 * it, and a scope can't be re-bound to a different organization. Jobs that cover several
 * organizations run one scope per organization, one after another.
 */
public final class OrganizationContext {

    private static final ScopedValue<Organization> CURRENT = ScopedValue.newInstance();

    private OrganizationContext() {}

    /** Returns the current organization, or throws {@link IllegalStateException} outside a scope. */
    public static Organization current() {
        if (!CURRENT.isBound()) {
            throw new IllegalStateException("No organization in context");
        }
        return CURRENT.get();
    }

    public static boolean isBound() {
        return CURRENT.isBound();
    }

    public static void run(Organization organization, Runnable op) {
        bind(organization).run(op);
    }

    public static <T, X extends Throwable> T call(Organization organization, ScopedValue.CallableOp<? extends T, X> op)
            throws X {
        return bind(organization).call(op);
    }

    private static ScopedValue.Carrier bind(Organization organization) {
        if (CURRENT.isBound() && !CURRENT.get().id().equals(organization.id())) {
            throw new IllegalStateException("Already running for organization "
                    + CURRENT.get().slug() + "; refusing to switch to " + organization.slug());
        }
        return ScopedValue.where(CURRENT, organization);
    }
}
