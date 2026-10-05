package com.playwrightforkubejs.protocol;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public final class Params {
    private Params() {
    }

    public static String string(Map<String, Object> params, String key, String fallback) {
        Object value = params == null ? null : params.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public static String requiredString(Map<String, Object> params, String key) {
        String value = string(params, key, null);
        if (value == null || value.isBlank()) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Missing parameter: " + key);
        }
        return value;
    }

    public static int integer(Map<String, Object> params, String key, int fallback) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Parameter " + key + " must be an integer");
        }
    }

    public static double number(Map<String, Object> params, String key, double fallback) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Parameter " + key + " must be a number");
        }
    }

    public static boolean bool(Map<String, Object> params, String key, boolean fallback) {
        Object value = params == null ? null : params.get(key);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    public static BlockPos blockPos(Map<String, Object> params) {
        if (params == null) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Missing block position");
        }
        Object value = params.get("pos");
        if (value instanceof BlockPos blockPos) {
            return blockPos;
        }
        if (value instanceof Vec3 vec3) {
            return BlockPos.containing(vec3);
        }
        if (value instanceof Map<?, ?> map) {
            return new BlockPos(intValue(map.get("x")), intValue(map.get("y")), intValue(map.get("z")));
        }
        if (value instanceof List<?> list && list.size() >= 3) {
            return new BlockPos(intValue(list.get(0)), intValue(list.get(1)), intValue(list.get(2)));
        }
        if (params.containsKey("x") && params.containsKey("y") && params.containsKey("z")) {
            return new BlockPos(integer(params, "x", 0), integer(params, "y", 0), integer(params, "z", 0));
        }
        throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Expected pos:{x,y,z} or x/y/z parameters");
    }

    public static Vec3 vec3(Map<String, Object> params) {
        if (params == null) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Missing vector");
        }
        Object value = params.get("pos");
        if (value instanceof Vec3 vec3) {
            return vec3;
        }
        if (value instanceof Map<?, ?> map) {
            return new Vec3(doubleValue(map.get("x")), doubleValue(map.get("y")), doubleValue(map.get("z")));
        }
        if (value instanceof List<?> list && list.size() >= 3) {
            return new Vec3(doubleValue(list.get(0)), doubleValue(list.get(1)), doubleValue(list.get(2)));
        }
        return new Vec3(number(params, "x", 0), number(params, "y", 0), number(params, "z", 0));
    }

    public static Direction direction(String value) {
        if (value == null || value.isBlank()) {
            return Direction.UP;
        }
        try {
            return Direction.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Unknown face: " + value);
        }
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception exception) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Position values must be numeric");
        }
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception exception) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Vector values must be numeric");
        }
    }
}
