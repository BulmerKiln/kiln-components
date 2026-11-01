/*
 * Copyright The Bulmer Kiln Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bulmerkiln.auth.user.service;

import static com.bulmerkiln.auth.user.service.UserDatabase.ACME;
import static com.bulmerkiln.auth.user.service.UserDatabase.GLOBEX;
import static org.assertj.core.api.Assertions.assertThat;

import com.bulmerkiln.auth.connector.domain.ConnectorId;
import com.bulmerkiln.auth.connector.domain.ExternalIdentity;
import com.bulmerkiln.auth.token.OpaqueSecret;
import com.bulmerkiln.auth.user.constant.AllowedDomainSource;
import com.bulmerkiln.auth.user.constant.SignInRefusal;
import com.bulmerkiln.auth.user.constant.SignInRoute;
import com.bulmerkiln.auth.user.domain.BootstrapAdmins;
import com.bulmerkiln.auth.user.domain.IssuerSubject;
import com.bulmerkiln.auth.user.dto.SignInRefusedResponse;
import com.bulmerkiln.auth.user.dto.SignInResult;
import com.bulmerkiln.auth.user.dto.SignedInResponse;
import com.bulmerkiln.auth.user.entity.AllowedDomainEntity;
import com.bulmerkiln.auth.user.entity.UserEntity;
import com.bulmerkiln.auth.user.store.UserStore;
import com.bulmerkiln.core.organization.Organization;
import com.bulmerkiln.core.organization.OrganizationContext;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs against a real PostgreSQL with the three roles, as Kiln's runtime role. */
class SignInServiceTest {

    private static final ConnectorId GOOGLE = new ConnectorId("google");
    private static final ConnectorId DEX = new ConnectorId("dex");
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String DEX_ISSUER = "https://dex.example.com";

    private UserDatabase db;
    private InvitationService invitations;
    private MemberService members;

    @BeforeEach
    void setUp() {
        db = new UserDatabase();
        invitations = new InvitationService(db.database(), db.clock());
        members = new MemberService(db.database(), db.organizations());
        new AllowedDomainService(db.database())
                .reconcile(List.of(new AllowedDomainEntity(
                        GOOGLE.getValue(), "acme.com", ACME.getId().getValue(), AllowedDomainSource.CONFIG)));
    }

    @AfterEach
    void close() {
        db.close();
    }

    /** AUTH-20: sign-ups are off; an unknown identity is refused and nothing is recorded. */
    @Test
    void unknownIdentityIsRefusedAndRecordsNothing() {
        SignInResult result = service(BootstrapAdmins.NONE).signIn(dex("stranger", "stranger@example.com"), null);

        assertThat(result).isEqualTo(new SignInRefusedResponse(SignInRefusal.UNKNOWN_IDENTITY));
        assertThat(db.count("users")).isZero();
        assertThat(db.count("user_identities")).isZero();
    }

    /** AUTH-20: a known identity signs in as its user, wherever the user came from. */
    @Test
    void knownIdentitySignsInAsItsUser() {
        SignInService service = service(BootstrapAdmins.NONE);
        UserEntity first = signedIn(service.signIn(google("alice", "alice@acme.com"), null));

        SignInResult again = service.signIn(google("alice", "alice@acme.com"), null);

        assertThat(again).isEqualTo(new SignedInResponse(first, SignInRoute.KNOWN_IDENTITY));
        assertThat(db.count("users")).isEqualTo(1);
    }

    /** AUTH-20: a disabled user is refused. */
    @Test
    void disabledUserIsRefused() {
        SignInService service = service(BootstrapAdmins.NONE);
        UserEntity alice = signedIn(service.signIn(google("alice", "alice@acme.com"), null));
        db.database().instanceTransaction(tx -> {
            UserStore.disable(tx, alice.getId());
            return Boolean.TRUE;
        });

        assertThat(service.signIn(google("alice", "alice@acme.com"), null))
                .isEqualTo(new SignInRefusedResponse(SignInRefusal.USER_DISABLED));
    }

    /** AUTH-21: an invite link creates the user through any connector, and joins its organization. */
    @Test
    void invitationCreatesUserAndJoinsItsOrganization() {
        SignInService service = service(BootstrapAdmins.NONE);
        OpaqueSecret link = invite(ACME);

        UserEntity bob = signedIn(service.signIn(dex("bob", null), link), SignInRoute.INVITATION);

        assertThat(isMember(ACME, bob)).isTrue();
        assertThat(isMember(GLOBEX, bob)).isFalse();
    }

    /** AUTH-21: an invite link works once. */
    @Test
    void invitationIsSingleUse() {
        SignInService service = service(BootstrapAdmins.NONE);
        OpaqueSecret link = invite(ACME);
        signedIn(service.signIn(dex("bob", null), link));

        assertThat(service.signIn(dex("mallory", null), link))
                .isEqualTo(new SignInRefusedResponse(SignInRefusal.INVITATION_INVALID));
    }

    /** AUTH-21: an invite link expires after 7 days. */
    @Test
    void invitationExpires() {
        SignInService service = service(BootstrapAdmins.NONE);
        OpaqueSecret link = invite(ACME);
        db.clock().advance(InvitationService.LIFETIME);

        assertThat(service.signIn(dex("bob", null), link))
                .isEqualTo(new SignInRefusedResponse(SignInRefusal.INVITATION_INVALID));
    }

    /** AUTH-21: a made-up invite link is refused, even for an email at an allowed domain. */
    @Test
    void unknownInvitationIsRefused() {
        SignInResult result =
                service(BootstrapAdmins.NONE).signIn(google("carol", "carol@acme.com"), OpaqueSecret.generate());

        assertThat(result).isEqualTo(new SignInRefusedResponse(SignInRefusal.INVITATION_INVALID));
        assertThat(db.count("users")).isZero();
    }

    /** AUTH-21: an existing user opening an invite link joins its organization. */
    @Test
    void existingUserJoinsInvitedOrganization() {
        SignInService service = service(BootstrapAdmins.NONE);
        UserEntity alice = signedIn(service.signIn(google("alice", "alice@acme.com"), null));
        OpaqueSecret link = invite(GLOBEX);

        signedIn(service.signIn(google("alice", "alice@acme.com"), link), SignInRoute.KNOWN_IDENTITY);

        assertThat(isMember(GLOBEX, alice)).isTrue();
    }

    /** AUTH-23: a verified email at an allowed domain, through its connector, creates a member. */
    @Test
    void allowedDomainCreatesMember() {
        UserEntity alice = signedIn(
                service(BootstrapAdmins.NONE).signIn(google("alice", "alice@acme.com"), null),
                SignInRoute.ALLOWED_DOMAIN);

        assertThat(isMember(ACME, alice)).isTrue();
        assertThat(isMember(GLOBEX, alice)).isFalse();
    }

    /** AUTH-23: the domain counts only through the connector it's allowed on. */
    @Test
    void allowedDomainThroughAnotherConnectorIsRefused() {
        assertThat(service(BootstrapAdmins.NONE).signIn(dex("alice", "alice@acme.com"), null))
                .isEqualTo(new SignInRefusedResponse(SignInRefusal.UNKNOWN_IDENTITY));
    }

    /** AUTH-23: domains match exactly, so a subdomain or a look-alike doesn't count. */
    @Test
    void allowedDomainMatchesExactly() {
        SignInService service = service(BootstrapAdmins.NONE);
        for (String email : List.of("eve@evil.acme.com", "eve@acme.com.evil", "eve@notacme.com")) {
            assertThat(service.signIn(google("eve-" + email.length(), email), null))
                    .as(email)
                    .isEqualTo(new SignInRefusedResponse(SignInRefusal.UNKNOWN_IDENTITY));
        }
    }

    /** AUTH-23: an email the IdP didn't verify never reaches Kiln, so it can't match a domain. */
    @Test
    void unverifiedEmailIsRefused() {
        assertThat(service(BootstrapAdmins.NONE).signIn(google("alice", null), null))
                .isEqualTo(new SignInRefusedResponse(SignInRefusal.UNKNOWN_IDENTITY));
    }

    /** AUTH-23: the domain's case doesn't matter. */
    @Test
    void allowedDomainIgnoresCase() {
        signedIn(service(BootstrapAdmins.NONE).signIn(google("alice", "Alice@ACME.com"), null));
    }

    /** AUTH-22: an email creates a user once; another identity with it is refused, not linked. */
    @Test
    void claimedEmailIsNeverMatchedAgain() {
        SignInService service = service(BootstrapAdmins.NONE);
        signedIn(service.signIn(google("alice", "alice@acme.com"), null));

        assertThat(service.signIn(google("alice-again", "ALICE@acme.com"), null))
                .isEqualTo(new SignInRefusedResponse(SignInRefusal.EMAIL_ALREADY_CLAIMED));
        assertThat(db.count("users")).isEqualTo(1);
    }

    /** AUTH-22: after the claim, an email change at the IdP doesn't move the account. */
    @Test
    void emailChangeKeepsTheUser() {
        SignInService service = service(BootstrapAdmins.NONE);
        UserEntity alice = signedIn(service.signIn(google("alice", "alice@acme.com"), null));

        assertThat(signedIn(service.signIn(google("alice", "alice.smith@acme.com"), null)))
                .isEqualTo(alice);
    }

    /** AUTH-24: a bootstrap email, through a connector allowing its domain, creates an instance admin. */
    @Test
    void bootstrapEmailCreatesInstanceAdmin() {
        BootstrapAdmins bootstrap = BootstrapAdmins.of(List.of("root@acme.com"), List.of());
        UserEntity root =
                signedIn(service(bootstrap).signIn(google("root", "root@acme.com"), null), SignInRoute.BOOTSTRAP_EMAIL);

        assertThat(admins(bootstrap).isInstanceAdmin(root.getId())).isTrue();
        assertThat(isMember(ACME, root)).isTrue();
    }

    /** AUTH-24: a bootstrap email through a connector that doesn't allow its domain is refused. */
    @Test
    void bootstrapEmailThroughAnotherConnectorIsRefused() {
        BootstrapAdmins bootstrap = BootstrapAdmins.of(List.of("root@acme.com"), List.of());

        assertThat(service(bootstrap).signIn(dex("root", "root@acme.com"), null))
                .isEqualTo(new SignInRefusedResponse(SignInRefusal.UNKNOWN_IDENTITY));
        assertThat(db.count("instance_admins")).isZero();
    }

    /** AUTH-24: a bootstrap subject is matched as an issuer and subject pair, never the subject alone. */
    @Test
    void bootstrapSubjectNeedsItsIssuer() {
        BootstrapAdmins bootstrap = BootstrapAdmins.of(List.of(), List.of(new IssuerSubject(DEX_ISSUER, "root")));
        SignInService service = service(bootstrap);

        assertThat(service.signIn(google("root", null), null))
                .isEqualTo(new SignInRefusedResponse(SignInRefusal.UNKNOWN_IDENTITY));
        UserEntity root = signedIn(service.signIn(dex("root", null), null), SignInRoute.BOOTSTRAP_SUBJECT);
        assertThat(admins(bootstrap).isInstanceAdmin(root.getId())).isTrue();
        assertThat(isMember(ACME, root)).isFalse();
    }

    /** AUTH-24: an existing user is granted instance admin once boot config lists them. */
    @Test
    void knownUserIsGrantedWhenListed() {
        UserEntity alice = signedIn(service(BootstrapAdmins.NONE).signIn(google("alice", "alice@acme.com"), null));
        BootstrapAdmins bootstrap = BootstrapAdmins.of(List.of("alice@acme.com"), List.of());

        signedIn(service(bootstrap).signIn(google("alice", "alice@acme.com"), null));

        assertThat(admins(bootstrap).isInstanceAdmin(alice.getId())).isTrue();
    }

    /** AUTH-25: users of one organization's allowed domain never become members of another. */
    @Test
    void allowedDomainsKeepOrganizationsApart() {
        new AllowedDomainService(db.database())
                .reconcile(List.of(
                        new AllowedDomainEntity(
                                GOOGLE.getValue(), "acme.com", ACME.getId().getValue(), AllowedDomainSource.CONFIG),
                        new AllowedDomainEntity(
                                GOOGLE.getValue(),
                                "globex.com",
                                GLOBEX.getId().getValue(),
                                AllowedDomainSource.CONFIG)));
        SignInService service = service(BootstrapAdmins.NONE);
        UserEntity alice = signedIn(service.signIn(google("alice", "alice@acme.com"), null));
        UserEntity hank = signedIn(service.signIn(google("hank", "hank@globex.com"), null));

        assertThat(isMember(ACME, alice)).isTrue();
        assertThat(isMember(GLOBEX, alice)).isFalse();
        assertThat(isMember(GLOBEX, hank)).isTrue();
        assertThat(isMember(ACME, hank)).isFalse();
        assertThat(members.organizationsOf(alice.getId())).containsExactly(ACME);
    }

    /** AUTH-23: removing a domain from the config stops it admitting anyone. */
    @Test
    void removedAllowedDomainNoLongerAdmits() {
        new AllowedDomainService(db.database()).reconcile(List.of());

        assertThat(service(BootstrapAdmins.NONE).signIn(google("alice", "alice@acme.com"), null))
                .isEqualTo(new SignInRefusedResponse(SignInRefusal.UNKNOWN_IDENTITY));
    }

    private SignInService service(BootstrapAdmins bootstrap) {
        return new SignInService(db.database(), db.organizations(), admins(bootstrap), db.clock());
    }

    private InstanceAdminService admins(BootstrapAdmins bootstrap) {
        return new InstanceAdminService(db.database(), bootstrap);
    }

    private OpaqueSecret invite(Organization organization) {
        db.clock().advance(Duration.ofSeconds(1));
        UUID admin = db.database()
                .instanceTransaction(tx -> UserStore.create(
                                tx, UUID.randomUUID(), "admin", db.clock().instant())
                        .getId());
        return invitations
                .invite(admin, "invitee@example.com", organization.getId())
                .getToken();
    }

    private boolean isMember(Organization organization, UserEntity user) {
        return OrganizationContext.call(organization, () -> members.activeMember(user.getId()))
                .isPresent();
    }

    private static UserEntity signedIn(SignInResult result) {
        assertThat(result).isInstanceOf(SignedInResponse.class);
        return ((SignedInResponse) result).getUser();
    }

    private static UserEntity signedIn(SignInResult result, SignInRoute route) {
        assertThat(result).isInstanceOf(SignedInResponse.class);
        assertThat(((SignedInResponse) result).getRoute()).isEqualTo(route);
        return ((SignedInResponse) result).getUser();
    }

    private static ExternalIdentity google(String subject, @Nullable String verifiedEmail) {
        return new ExternalIdentity(GOOGLE, GOOGLE_ISSUER, subject, verifiedEmail, Set.of());
    }

    private static ExternalIdentity dex(String subject, @Nullable String verifiedEmail) {
        return new ExternalIdentity(DEX, DEX_ISSUER, subject, verifiedEmail, Set.of());
    }
}
