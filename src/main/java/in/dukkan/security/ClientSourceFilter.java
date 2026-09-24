package in.dukkan.security;

import in.dukkan.common.ClientSourceHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClientSourceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String source = request.getHeader("X-Source");
            if (source == null || source.isBlank()) {
                source = request.getHeader("X-Client-Source");
            }
            if (source == null || source.isBlank()) {
                source = request.getParameter("source");
            }
            if (source == null || source.isBlank()) {
                String ua = request.getHeader("User-Agent");
                if (ua != null && (ua.contains("dukkan-mobile") || ua.contains("Expo") || ua.contains("okhttp"))) {
                    source = "M";
                }
            }
            ClientSourceHolder.setSource(source);
            chain.doFilter(request, response);
        } finally {
            ClientSourceHolder.clear();
        }
    }
}
