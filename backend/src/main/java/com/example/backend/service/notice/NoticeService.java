package com.example.backend.service.notice;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.api.PageResult;
import com.example.backend.entity.Notice;
import com.example.backend.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 站内通知：订单状态变更、收到评价、认证审核等事件推送到对应角色通知中心
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeMapper noticeMapper;

    /** 生成站内通知（在业务事务内调用，失败随主事务回滚） */
    public void send(Long userId, String type, String title, String content, Long bizId) {
        if (userId == null) {
            return;
        }
        Notice notice = new Notice();
        notice.setUserId(userId);
        notice.setType(type);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setBizId(bizId);
        notice.setIsRead(0);
        noticeMapper.insert(notice);
    }

    /** 我的通知分页（新到旧） */
    public PageResult<Notice> pageMy(Long userId, int page, int size) {
        IPage<Notice> result = noticeMapper.selectPage(new Page<>(page, size),
                Wrappers.<Notice>lambdaQuery()
                        .eq(Notice::getUserId, userId)
                        .orderByDesc(Notice::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /** 未读数 */
    public long unreadCount(Long userId) {
        Long cnt = noticeMapper.selectCount(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getUserId, userId).eq(Notice::getIsRead, 0));
        return cnt == null ? 0 : cnt;
    }

    /** 单条标记已读（归属校验） */
    public void markRead(Long userId, Long noticeId) {
        Notice notice = noticeMapper.selectById(noticeId);
        if (notice != null && notice.getUserId().equals(userId) && notice.getIsRead() == 0) {
            notice.setIsRead(1);
            noticeMapper.updateById(notice);
        }
    }

    /** 全部标记已读 */
    public void markAllRead(Long userId) {
        Notice update = new Notice();
        update.setIsRead(1);
        noticeMapper.update(update, Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getUserId, userId).eq(Notice::getIsRead, 0));
    }
}
