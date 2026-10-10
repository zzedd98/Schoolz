package ma.schoolz.erp.foundation.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = validIncomingId(request.getHeader(HEADER_NAME));
        response.setHeader(HEADER_NAME, correlationId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, correlationId)) {
            filterChain.doFilter(request, response);
        }
    }

    private String validIncomingId(String candidate) {
        if (candidate != null && candidate.matches("[A-Za-z0-9._-]{8,100}")) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
