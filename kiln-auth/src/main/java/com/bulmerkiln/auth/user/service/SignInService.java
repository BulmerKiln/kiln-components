/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.service;

import com.bulmerkiln.auth.connector.domain.ExternalIdentity;
import com.bulmerkiln.auth.token.OpaqueSecret;
import com.bulmerkiln.auth.user.constant.SignInRefusal;
import com.bulmerkiln.auth.user.constant.SignInRoute;
import com.bulmerkiln.auth.user.dto.SignInRefusedResponse;
import com.bulmerkiln.auth.user.dto.SignInResult;
import com.bulmerkiln.auth.user.dto.SignedInResponse;
import com.bulmerkiln.auth.user.entity.AllowedDomainEntity;
import com.bulmerkiln.auth.user.entity.InvitationEntity;
import com.bulmerkiln.auth.user.entity.UserEntity;
import com.bulmerkiln.auth.user.entity.UserIdentityEntity;
import com.bulmerkiln.auth.user.store.AllowedDomainStore;
import com.bulmerkiln.auth.user.store.InvitationStore;
import com.bulmerkiln.auth.user.store.MemberStore;
import com.bulmerkiln.auth.user.store.UserStore;
import com.bulmerkiln.auth.user.util.EmailUtils;
import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import com.bulmerkiln.core.organization.OrganizationDirectory;
import com.bulmerkiln.core.organization.OrganizationId;
import com.bulmerkiln.database.Database;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jspecify.annotations.Nullable;

/**
 * Decides who a verified IdP identity signs in as (AUTH-20). In order: the user who already has
 * the identity; a new user from an invite link; a new user from a bootstrap subject; a new user
 * from a verified email at an allowed domain, through that domain's connector, made an instance
 * admin if it's a bootstrap email. Anything else is refused, and nothing is recorded but a log line.
 *
 * <p>An email creates a user at most once. After that the user is found by issuer and subject only,
 * and another identity with the same email is refused rather than linked (AUTH-22).
 */
public final class SignInService {

    private static final System.Logger LOG = System.getLogger(SignInService.class.getName());

    private final Database database;
    private final OrganizationDirectory organizations;
    private final InstanceAdminService instanceAdmins;
    private final Clock clock;

    public SignInService(
            Database database, OrganizationDirectory organizations, InstanceAdminService instanceAdmins, Clock clock) {
        this.database = database;
        this.organizations = organizations;
        this.instanceAdmins = instanceAdmins;
        this.clock = clock;
    }

    /**
     * Signs the identity in.
     *
     * @param invitation the token from an invite link the person opened before signing in, if any
     */
    public SignInResult signIn(ExternalIdentity identity, @Nullable OpaqueSecret invitation) {
        SignInResult result;
        try {
            result = attempt(identity, invitation);
        } catch (IntegrityConstraintViolationException e) {
            // A concurrent sign-in created the same identity or claimed the same email: try once
            // more, and find what it created.
            result = attempt(identity, invitation);
        }
        if (result instanceof SignInRefusedResponse refused) {
            LOG.log(
                    System.Logger.Level.INFO,
                    "Sign-in through connector {0} refused: {1}",
                    identity.getConnector(),
                    refused.getReason());
        }
        return result;
    }

    /**
     * Finds the organization the sign-in may join first, then resolves it in one transaction bound
     * to that organization, so the user and their membership are created together or not at all.
     */
    private SignInResult attempt(ExternalIdentity identity, @Nullable OpaqueSecret invitation) {
        Instant now = clock.instant();
        Optional<Organization> joins = database.instanceTransaction(
                        tx -> organizationToJoin(tx, identity, invitation, now))
                .flatMap(id -> organizations.byId(new OrganizationId(id)));
        if (joins.isEmpty()) {
            return database.instanceTransaction(tx -> resolve(tx, identity, invitation, now, null));
        }
        UUID bound = joins.get().getId().getValue();
        return OrganizationContext.call(
                joins.get(), () -> database.transaction(tx -> resolve(tx, identity, invitation, now, bound)));
    }

    private static Optional<UUID> organizationToJoin(
            DSLContext tx, ExternalIdentity identity, @Nullable OpaqueSecret invitation, Instant now) {
        if (invitation != null) {
            Optional<InvitationEntity> invited = InvitationStore.claimable(tx, invitation.sha256(), now);
            if (invited.isPresent()) {
                return Optional.ofNullable(invited.get().getOrganizationId());
            }
        }
        String email = identity.getEmail();
        if (email == null
                || UserStore.identity(tx, identity.getIssuer(), identity.getSubject())
                        .isPresent()) {
            return Optional.empty();
        }
        return AllowedDomainStore.find(tx, identity.getConnector().getValue(), EmailUtils.domainOf(email))
                .map(AllowedDomainEntity::getOrganizationId);
    }

    private SignInResult resolve(
            DSLContext tx,
            ExternalIdentity identity,
            @Nullable OpaqueSecret invitation,
            Instant now,
            @Nullable UUID bound) {
        Optional<UserIdentityEntity> known = UserStore.identity(tx, identity.getIssuer(), identity.getSubject());
        if (known.isPresent()) {
            return signInKnown(tx, known.get(), invitation, now, bound);
        }
        if (invitation != null) {
            return signUpInvited(tx, identity, invitation, now, bound);
        }
        if (instanceAdmins.listsSubject(identity.getIssuer(), identity.getSubject())) {
            UserIdentityEntity created = createUser(tx, identity, null, now);
            instanceAdmins.grantIfListed(tx, created);
            return signedIn(tx, created, SignInRoute.BOOTSTRAP_SUBJECT);
        }
        return signUpByEmail(tx, identity, now, bound);
    }

    private SignInResult signInKnown(
            DSLContext tx,
            UserIdentityEntity known,
            @Nullable OpaqueSecret invitation,
            Instant now,
            @Nullable UUID bound) {
        UserEntity user = UserStore.byId(tx, known.getUserId()).orElseThrow();
        if (!user.active()) {
            return new SignInRefusedResponse(SignInRefusal.USER_DISABLED);
        }
        UserStore.touch(tx, known.getId(), now);
        if (invitation != null) {
            // An existing user opening an invite link joins its organization; a stale link is ignored.
            InvitationStore.claimable(tx, invitation.sha256(), now)
                    .filter(invited -> Objects.equals(invited.getOrganizationId(), bound))
                    .ifPresent(invited -> claim(tx, invited, user.getId(), now));
        }
        instanceAdmins.grantIfListed(tx, known);
        return new SignedInResponse(user, SignInRoute.KNOWN_IDENTITY);
    }

    private SignInResult signUpInvited(
            DSLContext tx, ExternalIdentity identity, OpaqueSecret invitation, Instant now, @Nullable UUID bound) {
        Optional<InvitationEntity> invited = InvitationStore.claimable(tx, invitation.sha256(), now)
                .filter(found -> Objects.equals(found.getOrganizationId(), bound));
        if (invited.isEmpty()) {
            return new SignInRefusedResponse(SignInRefusal.INVITATION_INVALID);
        }
        UserIdentityEntity created = createUser(tx, identity, null, now);
        claim(tx, invited.get(), created.getUserId(), now);
        instanceAdmins.grantIfListed(tx, created);
        return signedIn(tx, created, SignInRoute.INVITATION);
    }

    private SignInResult signUpByEmail(DSLContext tx, ExternalIdentity identity, Instant now, @Nullable UUID bound) {
        String email = identity.getEmail();
        if (email == null) {
            return new SignInRefusedResponse(SignInRefusal.UNKNOWN_IDENTITY);
        }
        Optional<AllowedDomainEntity> allowed = AllowedDomainStore.find(
                        tx, identity.getConnector().getValue(), EmailUtils.domainOf(email))
                .filter(domain -> domain.getOrganizationId().equals(bound));
        if (allowed.isEmpty()) {
            return new SignInRefusedResponse(SignInRefusal.UNKNOWN_IDENTITY);
        }
        if (UserStore.emailClaimed(tx, email)) {
            return new SignInRefusedResponse(SignInRefusal.EMAIL_ALREADY_CLAIMED);
        }
        UserIdentityEntity created = createUser(tx, identity, email, now);
        MemberStore.add(tx, created.getUserId(), now);
        instanceAdmins.grantIfListed(tx, created);
        SignInRoute route = instanceAdmins.listsEmail(email) ? SignInRoute.BOOTSTRAP_EMAIL : SignInRoute.ALLOWED_DOMAIN;
        return signedIn(tx, created, route);
    }

    /** Claims the invitation for the user, making them a member of its organization if it names one. */
    private static void claim(DSLContext tx, InvitationEntity invited, UUID userId, Instant now) {
        InvitationStore.markClaimed(tx, invited.getId(), userId, now);
        if (invited.getOrganizationId() != null) {
            MemberStore.add(tx, userId, now);
        }
    }

    private static UserIdentityEntity createUser(
            DSLContext tx, ExternalIdentity identity, @Nullable String claimedEmail, Instant now) {
        String email = identity.getEmail();
        UserEntity user = UserStore.create(tx, UUID.randomUUID(), email != null ? email : identity.getSubject(), now);
        return UserStore.addIdentity(
                tx,
                user.getId(),
                identity.getConnector().getValue(),
                identity.getIssuer(),
                identity.getSubject(),
                claimedEmail,
                now);
    }

    private static SignInResult signedIn(DSLContext tx, UserIdentityEntity identity, SignInRoute route) {
        return new SignedInResponse(UserStore.byId(tx, identity.getUserId()).orElseThrow(), route);
    }
}
