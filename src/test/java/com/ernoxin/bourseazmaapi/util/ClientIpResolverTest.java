package com.ernoxin.bourseazmaapi.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersFromUntrustedClients() {
        ClientIpResolver resolver = new ClientIpResolver("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void returnsLastUntrustedHopWhenRemoteProxyIsTrusted() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8,192.168.1.2");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "198.51.100.20, 192.168.1.2");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.20");
    }
}
