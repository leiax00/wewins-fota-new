package com.wewins.fota.domain.load.repository;

import com.wewins.fota.domain.load.model.entity.ControlParameter;

import java.util.Optional;

public interface ControlParameterRepository {

    Optional<ControlParameter> getGlobal();

    void saveGlobal(ControlParameter param);

    Optional<ControlParameter> getByProduct(Long productId);

    void saveByProduct(Long productId, ControlParameter param);
}
