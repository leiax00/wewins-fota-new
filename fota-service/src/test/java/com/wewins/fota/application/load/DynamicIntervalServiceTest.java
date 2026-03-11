package com.wewins.fota.application.load;

import com.wewins.fota.domain.load.model.entity.ControlParameter;
import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.model.enums.ProductPriority;
import com.wewins.fota.domain.load.repository.ControlParameterRepository;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import com.wewins.fota.domain.product.model.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
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

    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE;
    private static final int SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR;

    @Mock
    private SystemLoadIndicator loadIndicator;

    @Mock
    private ControlParameterRepository controlParameterRepository;

    @Mock
    private ProductRepository productRepository;

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
        Product product = Product.builder()
                .name("P100")
                .manufacturer("Wewins")
                .model("P100")
                .checkPeriodSeconds(6 * SECONDS_PER_HOUR)
                .build();
        product.setId(100L);
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));

        int interval = dynamicIntervalService.calculateCheckInterval(100L);

        assertThat(interval).isBetween(2000, 20000);
    }

    @Test
    void shouldFallBackToGlobalBoundsForUnknownProduct() {
        when(loadIndicator.getLoadLevel()).thenReturn(LoadLevel.CRITICAL);
        when(controlParameterRepository.getGlobal()).thenReturn(Optional.of(ControlParameter.builder()
                .checkIntervalMultiplier(2.0)
                .minCheckIntervalSeconds(30 * SECONDS_PER_MINUTE)
                .maxCheckIntervalSeconds(2 * SECONDS_PER_HOUR)
                .priority(ProductPriority.LOW)
                .build()));
        when(controlParameterRepository.getByProduct(200L)).thenReturn(Optional.empty());

        int interval = dynamicIntervalService.calculateCheckInterval(200L);

        assertThat(interval).isBetween(30 * SECONDS_PER_MINUTE, 2 * SECONDS_PER_HOUR);
    }

    @Test
    void shouldUseProtectedIntervalForSentinelFlow() {
        when(loadIndicator.getLoadLevel()).thenReturn(LoadLevel.NORMAL);
        when(controlParameterRepository.getGlobal()).thenReturn(Optional.of(ControlParameter.builder()
                .protectedCheckIntervalSeconds(4 * SECONDS_PER_HOUR)
                .protectedIntervalMultiplier(2.0)
                .minCheckIntervalSeconds(30 * SECONDS_PER_MINUTE)
                .maxCheckIntervalSeconds(SECONDS_PER_DAY)
                .priority(ProductPriority.NORMAL)
                .build()));

        int interval = dynamicIntervalService.calculateProtectedCheckInterval(null, "FLOW_QPS");

        assertThat(interval).isBetween(4 * SECONDS_PER_HOUR, SECONDS_PER_DAY);
    }
}
