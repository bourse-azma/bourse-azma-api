package com.ernoxin.bourseazmaapi.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class ClientIpResolver {

    private static final IpAddressMatcher ANY_IPV4 = new IpAddressMatcher("0.0.0.0/0");
    private static final IpAddressMatcher ANY_IPV6 = new IpAddressMatcher("::/0");
    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String remoteAddr = normalize(request.getRemoteAddr());
        if (remoteAddr == null) {
            return "unknown";
        }
        if (!isTrusted(remoteAddr)) {
            return remoteAddr;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remoteAddr;
        }
        String[] hops = forwarded.split(",");
        for (int index = hops.length - 1; index >= 0; index--) {
            String hop = normalize(hops[index]);
            if (hop != null && !isTrusted(hop)) {
                return hop;
            }
        }
        return remoteAddr;
    }

    private boolean isTrusted(String address) {
        return trustedProxies.stream().anyMatch(matcher -> matcher.matches(address));
    }

    private String normalize(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String normalized = address.trim();
        try {
            return ANY_IPV4.matches(normalized) || ANY_IPV6.matches(normalized) ? normalized : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
