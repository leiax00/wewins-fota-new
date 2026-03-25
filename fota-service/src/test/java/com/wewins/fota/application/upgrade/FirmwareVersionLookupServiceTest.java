package com.wewins.fota.application.upgrade;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FirmwareVersionLookupServiceTest {

    @Test
    void shouldRejectFirmwareWithoutHwWhenDeviceHasHw() {
        FirmwareTagSchemaProvider schemaProvider = mock(FirmwareTagSchemaProvider.class);
        when(schemaProvider.getTagKeys()).thenReturn(Set.of("hw"));

        FirmwareVersionLookupService service =
                new FirmwareVersionLookupService(null, null, null, schemaProvider);

        Boolean matched = ReflectionTestUtils.invokeMethod(
                service,
                "matchesFirmwareTags",
                Map.of(),
                Map.of("hw", "rev-a")
        );

        assertThat(matched).isFalse();
    }

    @Test
    void shouldMatchWhenConfiguredTagValuesAreEqual() {
        FirmwareTagSchemaProvider schemaProvider = mock(FirmwareTagSchemaProvider.class);
        when(schemaProvider.getTagKeys()).thenReturn(Set.of("hw"));

        FirmwareVersionLookupService service =
                new FirmwareVersionLookupService(null, null, null, schemaProvider);

        Boolean matched = ReflectionTestUtils.invokeMethod(
                service,
                "matchesFirmwareTags",
                Map.of("hw", "rev-a"),
                Map.of("hw", "rev-a")
        );

        assertThat(matched).isTrue();
    }
}
