package com.wewins.fota.application.load;

import com.wewins.fota.domain.load.model.entity.ControlParameter;
import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.model.enums.ProductPriority;
import com.wewins.fota.domain.load.repository.ControlParameterRepository;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicIntervalServiceTest {

    @Mock
    private SystemLoadIndicator loadIndicator;

    @Mock
    private ControlParameterRepository controlParameterRepository;

    @InjectMocks
    private DynamicIntervalService dynamicIntervalService;

    @Test
    void shouldApplyPriorityBiasAndMergedBounds() {
        when(loadIndicator.getLoadLevel()).thenReturn(LoadLevel.HIGH);
        when(controlParameterRepository.getGlobal()).thenReturn(Optional.of(ControlParameter.builder()
                .checkIntervalMultiplier(1.2)
                .minCheckIntervalSeconds(2000)
                .maxCheckIntervalSeconds(20000)
                .priority(ProductPriority.NORMAL)
                .build()));
        when(controlParameterRepository.getByProduct(100L)).thenReturn(Optional.of(ControlParameter.builder()
                .productId(100L)
                .intervalBias(0.8)
                .priority(ProductPriority.CRITICAL)
                .build()));

        int interval = dynamicIntervalService.calculateCheckInterval(100L, false);

        assertThat(interval).isBetween(2000, 20000);
    }

    @Test
    void shouldFallBackToGlobalBoundsForUnknownProduct() {
        when(loadIndicator.getLoadLevel()).thenReturn(LoadLevel.CRITICAL);
        when(controlParameterRepository.getGlobal()).thenReturn(Optional.of(ControlParameter.builder()
                .checkIntervalMultiplier(2.0)
                .minCheckIntervalSeconds(1800)
                .maxCheckIntervalSeconds(7200)
                .priority(ProductPriority.LOW)
                .build()));
        when(controlParameterRepository.getByProduct(200L)).thenReturn(Optional.empty());

        int interval = dynamicIntervalService.calculateCheckInterval(200L, true);

        assertThat(interval).isBetween(1800, 7200);
    }
}
