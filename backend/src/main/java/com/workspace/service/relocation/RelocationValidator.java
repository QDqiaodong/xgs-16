package com.workspace.service.relocation;

import com.workspace.entity.OfficeFurniture;
import com.workspace.entity.RelocationBatch;
import com.workspace.entity.RelocationItem;
import com.workspace.repository.OfficeFurnitureRepository;
import com.workspace.repository.RelocationItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 搬迁批次逐项校验。
 * 校验结果（冲突码 + 中文说明）写回每条 relocation_item，供确认前逐项展示；
 * 执行/重试前的最新数据复检在条目执行器中复用同样的规则。
 */
@Component
public class RelocationValidator {

    private final OfficeFurnitureRepository furnitureRepository;
    private final RelocationItemRepository itemRepository;

    public RelocationValidator(OfficeFurnitureRepository furnitureRepository,
                               RelocationItemRepository itemRepository) {
        this.furnitureRepository = furnitureRepository;
        this.itemRepository = itemRepository;
    }

    /**
     * 对批次全部条目执行逐项校验并持久化结果。
     *
     * @return 冲突条目数（0 表示全部通过）
     */
    @Transactional
    public int validateAndPersist(RelocationBatch batch, List<RelocationItem> items) {
        Map<Long, OfficeFurniture> furnitureMap = furnitureRepository.findAllById(
                        items.stream().map(RelocationItem::getFurnitureId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(OfficeFurniture::getId, Function.identity()));

        int conflicts = 0;
        for (RelocationItem item : items) {
            OfficeFurniture current = furnitureMap.get(item.getFurnitureId());
            List<String> codes = checkItem(batch, item, current, items, furnitureMap);
            if (codes.isEmpty()) {
                item.setValidateStatus(RelocationItem.VALIDATE_PASS);
                item.setValidateCodes(null);
                item.setValidateMessage("校验通过");
            } else {
                item.setValidateStatus(RelocationItem.VALIDATE_CONFLICT);
                item.setValidateCodes(String.join(",", codes));
                item.setValidateMessage(describeCodes(codes, batch.getTargetFloor()));
                conflicts++;
            }
        }
        itemRepository.saveAll(items);
        return conflicts;
    }

    private List<String> checkItem(RelocationBatch batch, RelocationItem item, OfficeFurniture current,
                                   List<RelocationItem> allItems, Map<Long, OfficeFurniture> furnitureMap) {
        List<String> codes = new ArrayList<>();
        if (current == null) {
            codes.add(RelocationItem.CODE_FURNITURE_DELETED);
            return codes;
        }
        if (!floorMatches(batch.getTargetFloor(), item.getTargetStationCode())) {
            codes.add(RelocationItem.CODE_FLOOR_MISMATCH);
        }
        if (bindingChanged(item, current)) {
            codes.add(RelocationItem.CODE_BINDING_CHANGED);
        }
        if (isStationOccupiedOutOfBatch(item, allItems, furnitureMap)) {
            codes.add(RelocationItem.CODE_STATION_OCCUPIED);
        }
        return codes;
    }

    /** 当前绑定与创建快照逐字段对比（楼层/工位/使用人/部门/绑定状态） */
    public static boolean bindingChanged(RelocationItem item, OfficeFurniture current) {
        return !equalsNullable(item.getSnapStationCode(), current.getStationCode())
                || !equalsNullable(item.getSnapEmployeeName(), current.getEmployeeName())
                || !equalsNullable(item.getSnapDepartment(), current.getDepartment())
                || !equalsNullable(item.getSnapBindStatus(), current.getBindStatus())
                || !equalsNullable(item.getSnapFloorNum(), current.getFloorNum());
    }

    private boolean isStationOccupiedOutOfBatch(RelocationItem item, List<RelocationItem> allItems,
                                                Map<Long, OfficeFurniture> furnitureMap) {
        Set<Long> batchFurnitureIds = allItems.stream()
                .map(RelocationItem::getFurnitureId).collect(Collectors.toSet());
        for (OfficeFurniture f : furnitureMap.values()) {
            if (isBound(f)
                    && item.getTargetStationCode().equals(f.getStationCode())
                    && !batchFurnitureIds.contains(f.getId())) {
                return true;
            }
        }
        return false;
    }

    /** 执行复检路径下的占用判断：目标工位上存在已绑定、且不属于本批次的家具 */
    public boolean occupiedByOtherBoundFurniture(String targetStationCode, Set<Long> batchFurnitureIds) {
        List<OfficeFurniture> occupants = furnitureRepository.findByStationCode(targetStationCode);
        for (OfficeFurniture f : occupants) {
            if (isBound(f) && !batchFurnitureIds.contains(f.getId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBound(OfficeFurniture f) {
        return f.getBindStatus() != null && f.getBindStatus() == 1;
    }

    /** 转成中文逐项说明 */
    public static String describeCodes(List<String> codes, Integer targetFloor) {
        return codes.stream().map(code -> {
            switch (code) {
                case RelocationItem.CODE_FURNITURE_DELETED:
                    return "家具已被删除";
                case RelocationItem.CODE_BINDING_CHANGED:
                    return "原绑定已变化（与批次创建时快照不一致）";
                case RelocationItem.CODE_STATION_OCCUPIED:
                    return "目标工位已被批次外家具占用";
                case RelocationItem.CODE_FLOOR_MISMATCH:
                    return "目标楼层与工位不匹配（工位应位于" + targetFloor + "层）";
                default:
                    return code;
            }
        }).collect(Collectors.joining("；"));
    }

    /**
     * 工位编号中的楼层是否与目标楼层一致。
     * 取编号开头连续数字（如 G12-A003 -> 12）；识别不出数字时不拦截（编号不遵循规范时交由人工确认）。
     */
    public static boolean floorMatches(Integer targetFloor, String stationCode) {
        if (targetFloor == null || stationCode == null) {
            return true;
        }
        String digits = "";
        for (int i = 0; i < stationCode.length(); i++) {
            char c = stationCode.charAt(i);
            if (c >= '0' && c <= '9') {
                digits += c;
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        if (digits.isEmpty()) {
            return true;
        }
        try {
            return Integer.parseInt(digits) == targetFloor;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static boolean equalsNullable(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
