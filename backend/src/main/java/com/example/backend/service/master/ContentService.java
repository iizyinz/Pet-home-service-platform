package com.example.backend.service.master;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.api.PageResult;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.example.backend.entity.Announcement;
import com.example.backend.entity.Banner;
import com.example.backend.mapper.AnnouncementMapper;
import com.example.backend.mapper.BannerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 首页内容：轮播图 / 公告 */
@Service
@RequiredArgsConstructor
public class ContentService {

    private final BannerMapper bannerMapper;
    private final AnnouncementMapper announcementMapper;

    public List<Banner> listBanners() {
        return bannerMapper.selectList(Wrappers.<Banner>lambdaQuery()
                .eq(Banner::getStatus, 1)
                .orderByAsc(Banner::getSort).orderByDesc(Banner::getId));
    }

    public PageResult<Announcement> pageAnnouncements(int page, int size) {
        IPage<Announcement> result = announcementMapper.selectPage(new Page<>(page, size),
                Wrappers.<Announcement>lambdaQuery()
                        .eq(Announcement::getStatus, 1)
                        .orderByDesc(Announcement::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    public Announcement getAnnouncement(Long id) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null || announcement.getStatus() == null || announcement.getStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "公告不存在或已下架");
        }
        return announcement;
    }
}
