package com.wewins.fota.domain.base.vo;

public record CacheLookupResult<T>(boolean hit, T value) {

    public static <T> CacheLookupResult<T> miss() {
        return new CacheLookupResult<>(false, null);
    }

    public static <T> CacheLookupResult<T> hit(T value) {
        return new CacheLookupResult<>(true, value);
    }

    public static <T> CacheLookupResult<T> hitNotFound() {
        return new CacheLookupResult<>(true, null);
    }
}
