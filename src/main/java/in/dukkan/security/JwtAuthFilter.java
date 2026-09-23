package in.dukkan.security;

import in.dukkan.config.SecurityConfig;
import in.dukkan.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository users;
    private final RequestMatcher publicPaths;

    public JwtAuthFilter(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
        List<RequestMatcher> matchers = new ArrayList<>();
        matchers.add(new AntPathRequestMatcher("/**", HttpMethod.OPTIONS.name()));
        matchers.add(new AntPathRequestMatcher("/uploads/**"));
        for (String path : SecurityConfig.PUBLIC_PATHS) {
            matchers.add(new AntPathRequestMatcher(path));
        }
        for (String path : SecurityConfig.PUBLIC_GET_PATHS) {
            matchers.add(new AntPathRequestMatcher(path, HttpMethod.GET.name()));
        }
        this.publicPaths = new OrRequestMatcher(matchers);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (publicPaths.matches(request)) {
            tryAuthenticate(request);
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            JwtAuthEntryPoint.writeUnauthorized(response);
            return;
        }

        if (!authenticateBearer(header.substring(7))) {
            JwtAuthEntryPoint.writeUnauthorized(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private void tryAuthenticate(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            authenticateBearer(header.substring(7));
        }
    }

    private boolean authenticateBearer(String token) {
        try {
            var claims = jwtService.parse(token);
            var user = users.findById(claims.getSubject());
            if (user.isEmpty()) {
                SecurityContextHolder.clearContext();
                return false;
            }
            var auth = new UsernamePasswordAuthenticationToken(
                    user.get().getId(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.get().getRole().name())));
            SecurityContextHolder.getContext().setAuthentication(auth);
            return true;
        } catch (Exception ignored) {
            SecurityContextHolder.clearContext();
            return false;
        }
    }
}
