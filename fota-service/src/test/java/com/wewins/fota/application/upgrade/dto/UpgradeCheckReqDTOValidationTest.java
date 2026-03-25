package com.wewins.fota.application.upgrade.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UpgradeCheckReqDTOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectMissingRequiredFields() {
        UpgradeCheckReqDTO request = new UpgradeCheckReqDTO();

        Set<ConstraintViolation<UpgradeCheckReqDTO>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("product 参数不能为空", "imei 参数不能为空", "version 参数不能为空");
    }

    @Test
    void shouldPassWhenRequiredFieldsPresent() {
        UpgradeCheckReqDTO request = new UpgradeCheckReqDTO();
        request.setProduct("demo-product");
        request.setImei("869123456789012");
        request.setVersion("1.0.0");

        Set<ConstraintViolation<UpgradeCheckReqDTO>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
