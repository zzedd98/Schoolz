package ma.schoolz.erp.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import ma.schoolz.erp.foundation.security.InvalidAccessTokenException;
import ma.schoolz.erp.foundation.security.JwtService;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.foundation.tenant.TenantContext;
import ma.schoolz.erp.identity.domain.UserAccount;
import ma.schoolz.erp.identity.persistence.UserAccountRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccessTokenFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public AccessTokenFilter(JwtService jwtService, UserAccountRepository accounts, Clock clock) {
        this.jwtService = jwtService;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.startsWith("Bearer ")) {
                authenticate(authorization.substring(7));
            }
            filterChain.doFilter(request, response);
        } catch (InvalidAccessTokenException exception) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter()
                    .write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401}");
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticate(String token) {
        SchoolPrincipal tokenPrincipal = jwtService.verify(token);
        UserAccount account =
                accounts.findByIdAndTenantId(tokenPrincipal.accountId(), tokenPrincipal.tenantId())
                        .filter(candidate -> candidate.canAuthenticate(clock.instant()))
                        .orElseThrow(InvalidAccessTokenException::new);

        SchoolPrincipal principal =
                new SchoolPrincipal(
                        account.id(),
                        account.tenantId(),
                        account.email(),
                        account.displayName(),
                        account.locale(),
                        account.roleCodes(),
                        account.permissions());
        List<SimpleGrantedAuthority> authorities =
                principal.permissions().stream().map(SimpleGrantedAuthority::new).toList();
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, token, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        TenantContext.set(principal.tenantId());
    }
}
