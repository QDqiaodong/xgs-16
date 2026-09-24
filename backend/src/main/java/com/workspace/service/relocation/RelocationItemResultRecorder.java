package com.workspace.service.relocation;

import com.workspace.entity.RelocationItem;
import com.workspace.repository.RelocationItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 失败/幂等结果落库独立事务。
 * 执行条目自己的事务在 flush 异常后持久化上下文可能已不可用，
 * 用独立事务保证"失败原因"一定能写入并与服务端最终状态一致。
 */
@Component
public class RelocationItemResultRecorder {

    private final RelocationItemRepository itemRepository;

    public RelocationItemResultRecorder(RelocationItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long itemId, String message, String codes) {
        itemRepository.findById(itemId).ifPresent(item -> {
            item.setResultStatus(RelocationItem.RESULT_FAILED);
            item.setValidateStatus(RelocationItem.VALIDATE_CONFLICT);
            item.setValidateCodes(codes);
            item.setValidateMessage(message);
            itemRepository.saveAndFlush(item);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long markIdempotent(Long itemId, String message) {
        RelocationItem item = itemRepository.findById(itemId).orElse(null);
        if (item == null) {
            return null;
        }
        item.setResultStatus(RelocationItem.RESULT_IDEMPOTENT);
        item.setValidateStatus(RelocationItem.VALIDATE_PASS);
        item.setValidateCodes(null);
        item.setValidateMessage(message);
        item.setExecutedTime(LocalDateTime.now());
        return itemRepository.saveAndFlush(item).getBindRecordId();
    }

    /** 幂等路径补绑台账记录ID（唯一键冲突收口时台账已存在） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachBindRecord(Long itemId, Long bindRecordId) {
        itemRepository.findById(itemId).ifPresent(item -> {
            item.setBindRecordId(bindRecordId);
            itemRepository.saveAndFlush(item);
        });
    }
}
