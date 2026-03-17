package com.wewins.fota.domain.base.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JsonValue implements Serializable {

    private static final long serialVersionUID = 1L;

    private String value;

    public boolean isEmpty() {
        return value == null || value.isBlank();
    }
}
