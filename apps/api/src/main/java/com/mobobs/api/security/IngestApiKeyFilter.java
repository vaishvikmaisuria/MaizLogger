package com.mobobs.api.security;

import com.mobobs.api.store.ApiKeyRepository;
import com.mobobs.api.store.ApiKeyRepository.ApiKeyRecord;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class IngestApiKeyFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final BCryptPasswordEncoder encoder;
    private final String headerName;

    public IngestApiKeyFilter(
            ApiKeyRepository apiKeyRepository,
            BCryptPasswordEncoder encoder,
            @Value("${mobobs.ingest.api-key-header}") String headerName) {
        this.apiKeyRepository = apiKeyRepository;
        this.encoder = encoder;
        this.headerName = headerName;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/v1/ingest");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader(headerName);
        if (apiKey == null || apiKey.length() < 8) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing or invalid API key");
            return;
        }

        String prefix = apiKey.substring(0, 8);
        List<ApiKeyRecord> candidates = apiKeyRepository.findActiveByPrefix(prefix);

        for (ApiKeyRecord candidate : candidates) {
            if (encoder.matches(apiKey, candidate.keyHash())) {
                request.setAttribute("appId", candidate.appId());
                filterChain.doFilter(request, response);
                return;
            }
        }

        response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid API key");
    }
}
