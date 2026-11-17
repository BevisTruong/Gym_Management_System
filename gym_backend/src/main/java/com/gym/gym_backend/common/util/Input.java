package com.gym.gym_backend.common.util;

import com.gym.gym_backend.common.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Pattern;

public final class Input {
    private Input() {
    }

    public static String text(Map<String, Object> data, String key, boolean required, int max) {
        Object raw = data.get(key);
        if (raw == null) {
            if (required)
                throw ApiException.badRequest(key + " là trường bắt buộc");
            return null;
        }
        String value = raw.toString().trim();
        if (value.isEmpty() && required)
            throw ApiException.badRequest(key + " không được để trống");
        if (value.length() > max)
            throw ApiException.badRequest(key + " không được dài quá " + max + " ký tự");
        return value.isEmpty() ? null : value;
    }

    public static long number(Map<String, Object> data, String key) {
        try {
            return Long.parseLong(data.get(key).toString());
        } catch (Exception ex) {
            throw ApiException.badRequest(key + " phải là số nguyên hợp lệ");
        }
    }

    public static int integer(Map<String, Object> data, String key, boolean required) {
        Object v = data.get(key);
        if (v == null && !required)
            return 0;
        try {
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            throw ApiException.badRequest(key + " phải là số nguyên hợp lệ");
        }
    }

    public static LocalDate date(Map<String, Object> data, String key) {
        try {
            return LocalDate.parse(data.get(key).toString());
        } catch (Exception ex) {
            throw ApiException.badRequest(key + " phải có định dạng YYYY-MM-DD");
        }
    }

    public static BigDecimal money(Map<String, Object> data, String key) {
        try {
            return new BigDecimal(data.get(key).toString());
        } catch (Exception ex) {
            throw ApiException.badRequest(key + " phải là số tiền hợp lệ");
        }
    }

    public static void phone(String value) {
        if (value == null || !Pattern.matches("\\+?[0-9][0-9 ().-]{6,19}", value))
            throw ApiException.badRequest("phone không đúng định dạng");
    }

    public static void email(String value, boolean required) {
        if (required && (value == null || value.isBlank()))
            throw ApiException.badRequest("email là trường bắt buộc");
        if (value != null && !Pattern.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", value))
            throw ApiException.badRequest("email không đúng định dạng");
    }

}
