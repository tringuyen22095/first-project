package personal.monolithic.config;

import static personal.monolithic.constants.Constants.CORRELATION_ID_CONTEXT_KEY;
import static personal.monolithic.constants.Constants.CORRELATION_ID_HEADER;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.ThreadContext;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);

        ThreadContext.put(CORRELATION_ID_CONTEXT_KEY, correlationId);
        MDC.put(CORRELATION_ID_CONTEXT_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        long startTime = System.currentTimeMillis();
        log.info("Request started method={} path={}", request.getMethod(), request.getRequestURI());

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("Request completed method={} path={} status={} durationMs={}", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), durationMs);
            MDC.remove(CORRELATION_ID_CONTEXT_KEY);
            ThreadContext.remove(CORRELATION_ID_CONTEXT_KEY);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        return correlationId.trim();
    }
}
