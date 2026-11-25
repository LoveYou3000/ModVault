package com.modvault.web;

import com.modvault.common.Ids;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Ensures every response carries a {@code request-id} header and that the id is present
 * in logs (MDC) for the whole request. Client-supplied ids are honored (FR-07).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String ATTR = "modvault.requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader("request-id");
        if (requestId == null || requestId.trim().isEmpty()) {
            requestId = Ids.requestId();
        }
        request.setAttribute(ATTR, requestId);
        response.setHeader("request-id", requestId);
        MDC.put("request_id", requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("request_id");
        }
    }
}
