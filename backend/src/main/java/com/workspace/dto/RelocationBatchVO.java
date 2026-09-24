package com.workspace.dto;

import com.workspace.entity.BindRecord;
import com.workspace.entity.RelocationBatch;
import com.workspace.entity.RelocationItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RelocationBatchVO {

    private Long id;
    private String batchNo;
    private String batchName;
    private Integer sourceFloor;
    private Integer targetFloor;
    private String status;
    private String targetDepartment;
    private String operatorName;
    private String remark;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private LocalDateTime lastExecutedTime;
    private LocalDateTime confirmedTime;
    private LocalDateTime completedTime;
    private LocalDateTime cancelledTime;
    private LocalDateTime createTime;
    private Integer version;

    /** 最近一次逐项校验是否全部通过（用于前端高亮"可确认"） */
    private Boolean validationPassed;
    private Integer conflictCount;

    private List<ItemVO> items;
    private List<BindRecord> bindRecords;

    @Data
    public static class ItemVO {
        private Long id;
        private Long furnitureId;
        private String furnitureCode;
        private String furnitureType;
        private String targetStationCode;
        private String targetEmployeeName;
        private String targetDepartment;

        // 原绑定快照
        private Integer snapFloorNum;
        private String snapStationCode;
        private String snapEmployeeName;
        private String snapDepartment;
        private Integer snapBindStatus;

        // 校验与执行结果
        private String resultStatus;
        private String validateStatus;
        private String validateCodes;
        private String validateMessage;
        private Long bindRecordId;
        private LocalDateTime executedTime;

        // 当前实时绑定（与快照对照）
        private Integer currentFloorNum;
        private String currentStationCode;
        private String currentEmployeeName;
        private String currentDepartment;
        private Integer currentBindStatus;
        private Boolean furnitureExists;
    }

    public static ItemVO toItemVO(RelocationItem item) {
        ItemVO vo = new ItemVO();
        vo.setId(item.getId());
        vo.setFurnitureId(item.getFurnitureId());
        vo.setFurnitureCode(item.getFurnitureCode());
        vo.setFurnitureType(item.getFurnitureType());
        vo.setTargetStationCode(item.getTargetStationCode());
        vo.setTargetEmployeeName(item.getTargetEmployeeName());
        vo.setTargetDepartment(item.getTargetDepartment());
        vo.setSnapFloorNum(item.getSnapFloorNum());
        vo.setSnapStationCode(item.getSnapStationCode());
        vo.setSnapEmployeeName(item.getSnapEmployeeName());
        vo.setSnapDepartment(item.getSnapDepartment());
        vo.setSnapBindStatus(item.getSnapBindStatus());
        vo.setResultStatus(item.getResultStatus());
        vo.setValidateStatus(item.getValidateStatus());
        vo.setValidateCodes(item.getValidateCodes());
        vo.setValidateMessage(item.getValidateMessage());
        vo.setBindRecordId(item.getBindRecordId());
        vo.setExecutedTime(item.getExecutedTime());
        return vo;
    }

    public static RelocationBatchVO from(RelocationBatch batch) {
        RelocationBatchVO vo = new RelocationBatchVO();
        vo.setId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setBatchName(batch.getBatchName());
        vo.setSourceFloor(batch.getSourceFloor());
        vo.setTargetFloor(batch.getTargetFloor());
        vo.setStatus(batch.getStatus());
        vo.setTargetDepartment(batch.getTargetDepartment());
        vo.setOperatorName(batch.getOperatorName());
        vo.setRemark(batch.getRemark());
        vo.setTotalCount(batch.getTotalCount());
        vo.setSuccessCount(batch.getSuccessCount());
        vo.setFailCount(batch.getFailCount());
        vo.setLastExecutedTime(batch.getLastExecutedTime());
        vo.setConfirmedTime(batch.getConfirmedTime());
        vo.setCompletedTime(batch.getCompletedTime());
        vo.setCancelledTime(batch.getCancelledTime());
        vo.setCreateTime(batch.getCreateTime());
        vo.setVersion(batch.getVersion());
        return vo;
    }
}
