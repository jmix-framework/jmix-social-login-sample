package com.company.sociallogin.security;

import com.company.sociallogin.entity.Account;
import com.company.sociallogin.entity.AccountType;
import com.company.sociallogin.entity.User;
import io.jmix.core.DataManager;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.security.role.RoleGrantedAuthorityUtils;
import io.jmix.securityflowui.security.FlowuiVaadinWebSecurity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.DefaultRedirectStrategy;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

@EnableWebSecurity
@Configuration
public class OAuth2SecurityConfiguration extends FlowuiVaadinWebSecurity {

    private static final Logger log = LoggerFactory.getLogger(OAuth2SecurityConfiguration.class);

    private final RoleGrantedAuthorityUtils authorityUtils;
    private final UnconstrainedDataManager unconstrainedDataManager;

    public OAuth2SecurityConfiguration(RoleGrantedAuthorityUtils authorityUtils, UnconstrainedDataManager unconstrainedDataManager) {
        this.authorityUtils = authorityUtils;
        this.unconstrainedDataManager = unconstrainedDataManager;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);

        http.oauth2Login(configurer ->
                configurer
                        .loginPage(getLoginPath())
                        .userInfoEndpoint(userInfoEndpointConfig ->
                                userInfoEndpointConfig
                                        .userService(oauth2UserService())
                                        .oidcUserService(oidcUserService()))
                        .successHandler(this::onAuthenticationSuccess)
        );
    }

    private void onAuthenticationSuccess(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        // redirect to the main screen after successful authentication using auth provider
        new DefaultRedirectStrategy().sendRedirect(request, response, "/");
    }

    private User getOrCreateUser(String accountIdent, String providerId, String email, String[] names) {
        AccountType accountType = AccountType.fromId(providerId);
        if (accountType == null) {
            throw new UnknownAuthProviderException(providerId);
        }
        User jmixUser = unconstrainedDataManager.load(User.class)
                .query("select u from User u join u.accounts a where (a.ident = :accountIdent and a.type = :accountType) or u.email = :email")
                .parameter("accountIdent", accountIdent)
                .parameter("accountType", accountType)
                .parameter("email", email)
                .optional()
                .orElseGet(() -> {
                    User user = unconstrainedDataManager.create(User.class);
                    user.setEmail(email);
                    return user;
                });
        jmixUser.setUsername(email != null ? email : accountIdent);
        if (names.length > 1) {
            jmixUser.setFirstName(names[0]);
            jmixUser.setLastName(names[1]);
        }
        jmixUser = unconstrainedDataManager.save(jmixUser);
        if (! jmixUser.getAccounts().stream().anyMatch(a -> a.getType().equals(AccountType.fromId(providerId)))) {
            Account account = unconstrainedDataManager.create(Account.class);
            account.setType(AccountType.fromId(providerId));
            account.setIdent(accountIdent);
            account.setUser(jmixUser);
            account = unconstrainedDataManager.save(account);
            jmixUser.getAccounts().add(account);
        }
        if (jmixUser.getAuthorities().isEmpty()) {
            jmixUser.setAuthorities(getDefaultGrantedAuthorities());
        }
        return jmixUser;
    }

    /**
     * Service responsible for loading OAuth2 users
     */
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        return (userRequest) -> {
            OAuth2User oAuth2User = delegate.loadUser(userRequest);

            Integer id = oAuth2User.getAttribute("id");
            String accountIdent = String.valueOf(id);
            String registrationId = userRequest.getClientRegistration().getRegistrationId();
            String email = oAuth2User.getAttribute("email");
            String name = oAuth2User.getAttribute("name");
            String[] names = name.split(" ");

            return getOrCreateUser(accountIdent, registrationId, email, names);
        };
    }

    /**
     * Service responsible for loading OIDC users (Google uses OIDC protocol)
     */
    private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        OidcUserService delegate = new OidcUserService();
        return (userRequest) -> {
            OidcUser oidcUser = delegate.loadUser(userRequest);

            String accountIdent = oidcUser.getSubject();
            String registrationId = userRequest.getClientRegistration().getRegistrationId();
            String email = oidcUser.getEmail();
            String[] names = new String[] { oidcUser.getAttribute("given_name"), oidcUser.getAttribute("family_name") };

            return getOrCreateUser(accountIdent, registrationId, email, names);
        };
    }

    /**
     * Builds granted authority list that grants access to the FullAccess role
     */
    private Collection<GrantedAuthority> getDefaultGrantedAuthorities() {
        return List.of(authorityUtils.createResourceRoleGrantedAuthority(FullAccessRole.CODE));
    }
}