package com.bot.fsap.flowable.web.logging;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Captures the full HTTP exchange and emits it to a dedicated logger for auditing purposes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger REQUEST_RESPONSE_LOGGER = LoggerFactory.getLogger("HttpExchangeLogger");
    private static final Set<String> SENSITIVE_HEADERS = Set.of("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key");
    private static final int MAX_PAYLOAD_LENGTH = 16 * 1024; // prevent excessively large log lines
    private static final String TRACE_ID_HEADER = "X-Request-Id";
    private static final String TRACE_ID_ATTRIBUTE = RequestResponseLoggingFilter.class.getName() + ".TRACE_ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        Instant start = Instant.now();
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            logExchange(requestWrapper, responseWrapper, Duration.between(start, Instant.now()));
            responseWrapper.copyBodyToResponse();
        }
    }

    private void logExchange(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, Duration duration) {
        if (!REQUEST_RESPONSE_LOGGER.isInfoEnabled()) {
            return;
        }
        String traceId = getOrCreateTraceId(request);
        response.setHeader(TRACE_ID_HEADER, traceId);
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        if (query == null) {
            query = "";
        }
        String remote = resolveRemoteAddress(request);
        String principal = request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();

        Map<String, List<String>> requestHeaders = extractHeaders(request);
        Map<String, List<String>> responseHeaders = extractHeaders(response);

        String payload = renderPayload(request.getContentAsByteArray(), request.getContentType());
        String responsePayload = renderPayload(response.getContentAsByteArray(), response.getContentType());

        String requestParams = formatParameters(request.getParameterMap());

        REQUEST_RESPONSE_LOGGER.info(
                "HTTP id={} method={} uri={} query={} remote={} user={} status={} durationMs={} reqHeaders={} reqParams={} reqBody={} respHeaders={} respBody={}",
                traceId,
                method,
                uri,
                query,
                remote,
                principal,
                response.getStatus(),
                duration.toMillis(),
                requestHeaders,
                requestParams,
                payload,
                responseHeaders,
                responsePayload
        );
    }

    private String getOrCreateTraceId(HttpServletRequest request) {
        Object attached = request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (attached instanceof String existing && !existing.isBlank()) {
            return existing;
        }

        String headerValue = request.getHeader(TRACE_ID_HEADER);
        if (headerValue != null && !headerValue.isBlank()) {
            request.setAttribute(TRACE_ID_ATTRIBUTE, headerValue);
            return headerValue;
        }

        String generated = UUID.randomUUID().toString();
        request.setAttribute(TRACE_ID_ATTRIBUTE, generated);
        return generated;
    }

    private Map<String, List<String>> extractHeaders(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            List<String> values = Collections.list(request.getHeaders(headerName));
            headers.put(headerName, maskIfNecessary(headerName, values));
        }
        return headers;
    }

    private Map<String, List<String>> extractHeaders(ContentCachingResponseWrapper response) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String headerName : response.getHeaderNames()) {
            headers.put(headerName, new ArrayList<>(response.getHeaders(headerName)));
        }
        return headers;
    }

    private List<String> maskIfNecessary(String headerName, List<String> values) {
        if (SENSITIVE_HEADERS.contains(headerName.toLowerCase())) {
            return values.stream().map(value -> value == null ? null : "***masked***").collect(Collectors.toList());
        }
        return values;
    }

    private String formatParameters(Map<String, String[]> parameterMap) {
        if (parameterMap.isEmpty()) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        parameterMap.forEach((key, values) -> {
            String value = values == null ? "null" : String.join("|", values);
            joiner.add(key + '=' + value);
        });
        return joiner.toString();
    }

    private String renderPayload(@Nullable byte[] payload, @Nullable String contentType) {
        if (payload == null || payload.length == 0) {
            return "<empty>";
        }
        boolean isTextual = isTextual(contentType);
        if (!isTextual) {
            return String.format("<binary content length=%d type=%s>", payload.length, contentType);
        }

        int length = Math.min(payload.length, MAX_PAYLOAD_LENGTH);
        String body = new String(payload, 0, length, StandardCharsets.UTF_8);
        if (payload.length > MAX_PAYLOAD_LENGTH) {
            body = body + "...(truncated)";
        }
        return body;
    }

    private boolean isTextual(@Nullable String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            if ("text".equalsIgnoreCase(mediaType.getType())) {
                return true;
            }
            String subtype = mediaType.getSubtype().toLowerCase();
            return subtype.contains("json")
                    || subtype.contains("xml")
                    || subtype.contains("form")
                    || subtype.contains("text")
                    || subtype.contains("javascript")
                    || subtype.contains("x-www-form-urlencoded");
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }

    private String resolveRemoteAddress(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) {
            return header;
        }
        header = request.getHeader("X-Real-IP");
        if (header != null && !header.isBlank()) {
            return header;
        }
        return request.getRemoteAddr();
    }
}
