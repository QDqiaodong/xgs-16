package com.workspace.common;

/**
 * 搬迁校验/执行逐项失败原因码。前端据此展示具体问题，执行重试时也复用同一套语义。
 */
public final class RelocationFailCode {

    /** 家具已被删除 */
    public static final String FURNITURE_DELETED = "FURNITURE_DELETED";
    /** 创建时的原绑定快照已发生变化（家具被重新绑定/解绑/换工位等） */
    public static final String SNAPSHOT_CHANGED = "SNAPSHOT_CHANGED";
    /** 目标工位已被本批次之外的家具占用 */
    public static final String STATION_OCCUPIED = "STATION_OCCUPIED";
    /** 目标工位与迁入楼层不匹配 */
    public static final String FLOOR_MISMATCH = "FLOOR_MISMATCH";
    /** 家具当前已不在迁出楼层（楼层被改动） */
    public static final String SOURCE_FLOOR_MISMATCH = "SOURCE_FLOOR_MISMATCH";
    /** 家具已被其他未结束批次占用（正常由锁表拦截，防御性校验） */
    public static final String FURNITURE_LOCKED = "FURNITURE_LOCKED";
    /** 目标工位在批次内重复（结构性问题） */
    public static final String DUPLICATE_TARGET_STATION = "DUPLICATE_TARGET_STATION";

    public static String text(String code) {
        return switch (code) {
            case FURNITURE_DELETED -> "家具已被删除";
            case SNAPSHOT_CHANGED -> "原绑定已变化";
            case STATION_OCCUPIED -> "目标工位被批次外家具占用";
            case FLOOR_MISMATCH -> "目标楼层与工位不匹配";
            case SOURCE_FLOOR_MISMATCH -> "家具当前不在迁出楼层";
            case FURNITURE_LOCKED -> "家具已在其他未结束批次中";
            case DUPLICATE_TARGET_STATION -> "目标工位在批次内重复";
            default -> code;
        };
    }

    private RelocationFailCode() {
    }
}
