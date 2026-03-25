package com.wewins.fota.adapter.api.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class UpgradeCheckRequestNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UpgradeCheckRequestNormalizer normalizer = new UpgradeCheckRequestNormalizer(objectMapper);

    @Test
    void shouldCaptureUnknownTopLevelJsonFieldsIntoExtTags() throws Exception {
        String body = """
                {
                  "product": "demo-product",
                  "imei": "869123456789012",
                  "version": "1.0.0",
                  "hw": "rev-a",
                  "board": "rk3568",
                  "extTags": {
                    "region": "CN"
                  }
                }
                """;

        UpgradeCheckReqDTO request = objectMapper.readValue(body, UpgradeCheckReqDTO.class);
        request = normalizer.normalizeBodyRequest(request);

        assertThat(request.getHardwareVersion()).isEqualTo("rev-a");
        assertThat(request.getExtTagValue("board")).isEqualTo("rk3568");
        assertThat(request.getExtTagValue("region")).isEqualTo("CN");
    }

    @Test
    void shouldMergeGetExtraParamsIntoExtTags() {
        UpgradeCheckReqDTO request = new UpgradeCheckReqDTO();
        request.setProduct("demo-product");
        request.setImei("869123456789012");
        request.setVersion("1.0.0");

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addParameter("product", "demo-product");
        httpRequest.addParameter("imei", "869123456789012");
        httpRequest.addParameter("version", "1.0.0");
        httpRequest.addParameter("hw", "rev-b");
        httpRequest.addParameter("ext.region", "EU");
        httpRequest.addParameter("extTags", "{\"channel\":\"beta\"}");

        UpgradeCheckReqDTO normalized = normalizer.normalizeQueryRequest(request, httpRequest);

        assertThat(normalized.getHardwareVersion()).isEqualTo("rev-b");
        assertThat(normalized.getExtTagValue("region")).isEqualTo("EU");
        assertThat(normalized.getExtTagValue("channel")).isEqualTo("beta");
    }
}
