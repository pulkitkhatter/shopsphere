package com.shopsphere.common.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private String run(String incomingHeader, AtomicReference<String> mdcDuringRequest) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (incomingHeader != null) request.addHeader(CorrelationIdFilter.HEADER, incomingHeader);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcDuringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            }
        });
        return response.getHeader(CorrelationIdFilter.HEADER);
    }

    @Test
    void generatesAnId_whenNoneSupplied_andExposesItInMdcAndResponse() throws Exception {
        AtomicReference<String> mdc = new AtomicReference<>();

        String responseId = run(null, mdc);

        assertThat(responseId).isNotBlank();
        assertThat(mdc.get()).isEqualTo(responseId);
    }

    @Test
    void keepsAWellFormedIncomingId() throws Exception {
        assertThat(run("trace-12345678", new AtomicReference<>())).isEqualTo("trace-12345678");
    }

    @Test
    void replacesAMaliciousId_toPreventLogForging() throws Exception {
        String responseId = run("abc\r\nINFO fake log line", new AtomicReference<>());

        assertThat(responseId).matches("[A-Za-z0-9-]{8,64}").doesNotContain("fake");
    }

    @Test
    void clearsMdcAfterTheRequest() throws Exception {
        run(null, new AtomicReference<>());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
