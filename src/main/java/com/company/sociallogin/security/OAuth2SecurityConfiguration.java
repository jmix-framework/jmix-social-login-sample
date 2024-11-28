package com.company.sociallogin.security;

import com.company.sociallogin.entity.AuthenticationType;
import com.company.sociallogin.entity.User;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.security.role.RoleGrantedAuthorityUtils;
import io.jmix.securityflowui.security.FlowuiVaadinWebSecurity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
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

// tag::class[]
@EnableWebSecurity
@Configuration
public class OAuth2SecurityConfiguration extends FlowuiVaadinWebSecurity {

    @Autowired
    private RoleGrantedAuthorityUtils authorityUtils;
    @Autowired
    private UnconstrainedDataManager dataManager;
    // ...
    // end::class[]
    // tag::configure[]
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
    // end::configure[]

    // tag::onAuthenticationSuccess[]
    private void onAuthenticationSuccess(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        // Redirect to the main view after successful authentication
        new DefaultRedirectStrategy().sendRedirect(request, response, "/");
    }
    // end::onAuthenticationSuccess[]

    // tag::oauth2UserService[]
    // Returns a method that loads GitHub users
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        return (userRequest) -> {
            // Delegate to the default implementation to load an external user
            OAuth2User oAuth2User = delegate.loadUser(userRequest);

            // Find or create a user with username equal to the GitHub ID
            Integer githubId = oAuth2User.getAttribute("id");
            User jmixUser = loadUserByUsername(String.valueOf(githubId));

            // Update the user with information from GitHub
            jmixUser.setAuthenticationType(AuthenticationType.GITHUB);
            jmixUser.setEmail(oAuth2User.getAttribute("email"));
            String nameAttr = oAuth2User.getAttribute("name");
            if (nameAttr != null) {
                int idx = nameAttr.indexOf(" ");
                if (idx > 0) {
                    jmixUser.setFirstName(nameAttr.substring(0, idx));
                    jmixUser.setLastName(nameAttr.substring(idx + 1));
                } else {
                    jmixUser.setLastName(nameAttr);
                }
            }

            // Save the user to the database and assign roles
            User savedJmixUser = dataManager.save(jmixUser);
            savedJmixUser.setAuthorities(getDefaultGrantedAuthorities());
            return savedJmixUser;
        };
    }
    // end::oauth2UserService[]

    // tag::oidcUserService[]
    // Returns a method that loads Google users
    private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        OidcUserService delegate = new OidcUserService();
        return (userRequest) -> {
            // Delegate to the default implementation to load an external user
            OidcUser oidcUser = delegate.loadUser(userRequest);

            // Find or create a user with username equal to the Google ID
            String googleId = oidcUser.getSubject();
            User jmixUser = loadUserByUsername(googleId);

            // Update the user with information from Google
            jmixUser.setAuthenticationType(AuthenticationType.GOOGLE);
            jmixUser.setEmail(oidcUser.getEmail());
            jmixUser.setFirstName(oidcUser.getAttribute("given_name"));
            jmixUser.setLastName(oidcUser.getAttribute("family_name"));

            // Update the user to the database and assign roles
            User savedJmixUser = dataManager.save(jmixUser);
            savedJmixUser.setAuthorities(getDefaultGrantedAuthorities());
            return savedJmixUser;
        };
    }
    // end::oidcUserService[]

    // tag::loadUserByUsername[]
    // Loads user by username or creates a new user
    private User loadUserByUsername(String username) {
        return dataManager.load(User.class)
                .query("e.username = ?1", username)
                .optional()
                .orElseGet(() -> {
                    User user = dataManager.create(User.class);
                    user.setUsername(username);
                    return user;
                });
    }
    // end::loadUserByUsername[]

    // tag::getDefaultGrantedAuthorities[]
    // Builds granted authority list to assign default roles to the user
    private Collection<GrantedAuthority> getDefaultGrantedAuthorities() {
        return List.of(
                authorityUtils.createResourceRoleGrantedAuthority(FullAccessRole.CODE)
        );
    }
    // end::getDefaultGrantedAuthorities[]
}