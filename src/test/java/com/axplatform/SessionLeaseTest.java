package com.axplatform;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import com.axplatform.security.*;import com.axplatform.common.Problem;
import static org.assertj.core.api.Assertions.*;
class SessionLeaseTest {
 private final CustomerPrincipal subject=new CustomerPrincipal(1,0,0,"EMAIL","Test");
 private MockHttpSession authenticated(){var session=new MockHttpSession();var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(subject,"",AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);return session;}
 @Test void authenticatedOriginalSessionIsAccepted(){assertThatCode(()->SessionLease.require(authenticated(),subject)).doesNotThrowAnyException();}
 @Test void logoutInvalidationStopsLongResponse(){var session=authenticated();session.invalidate();assertThatThrownBy(()->SessionLease.require(session,subject)).isInstanceOf(Problem.class);}
 @Test void anotherLoginInSameSessionStopsOriginalResponse(){var session=authenticated();var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new CustomerPrincipal(2,0,0,"EMAIL","Other"),"",AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);assertThatThrownBy(()->SessionLease.require(session,subject)).isInstanceOf(Problem.class);}
 @Test void missingContextFailsClosed(){assertThatThrownBy(()->SessionLease.require(new MockHttpSession(),subject)).isInstanceOf(Problem.class);}
 @Test void samePrincipalLoginWithRotatedSessionIdStopsOriginalResponse(){var session=authenticated();String original=SessionLease.captureId(session,subject);session.changeSessionId();assertThatThrownBy(()->SessionLease.require(session,subject,original)).isInstanceOf(Problem.class);}
}
