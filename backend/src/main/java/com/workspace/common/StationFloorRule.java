package com.workspace.common;

import cn.hutool.core.util.StrUtil;

/**
 * 工位编号与楼层的对应规则。
 * 现有工位编号形如 G1-A001 / G2-MT01 / G5-EX02，G 后紧跟的数字即楼层号，
 * 与 office_furniture.floor_num 对应。搬迁批次必须满足目标工位属于迁入楼层。
 */
public final class StationFloorRule {

    public static Integer parseFloor(String stationCode) {
        if (StrUtil.isBlank(stationCode)) {
            return null;
        }
        String code = stationCode.trim().toUpperCase();
        if (!code.startsWith("G")) {
            return null;
        }
        int i = 1;
        StringBuilder digits = new StringBuilder();
        while (i < code.length() && Character.isDigit(code.charAt(i))) {
            digits.append(code.charAt(i));
            i++;
        }
        if (digits.length() == 0) {
            return null;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean matchesFloor(String stationCode, Integer floorNum) {
        if (floorNum == null) {
            return false;
        }
        return floorNum.equals(parseFloor(stationCode));
    }

    private StationFloorRule() {
    }
}
