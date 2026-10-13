package com.example.backend.service.master;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.api.PageResult;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.example.backend.entity.ServiceArea;
import com.example.backend.entity.ServiceItem;
import com.example.backend.entity.ServiceType;
import com.example.backend.mapper.ServiceAreaMapper;
import com.example.backend.mapper.ServiceItemMapper;
import com.example.backend.mapper.ServiceTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 服务浏览：服务类型 / 服务区域 / 服务项目查询
 */
@Service
@RequiredArgsConstructor
public class ServiceCatalogService {

    private final ServiceTypeMapper typeMapper;
    private final ServiceAreaMapper areaMapper;
    private final ServiceItemMapper itemMapper;

    /** 服务类型（启用状态），按 sort 排序 —— 首页分类入口 */
    public List<ServiceType> listTypes() {
        return typeMapper.selectList(Wrappers.<ServiceType>lambdaQuery()
                .eq(ServiceType::getStatus, 1).orderByAsc(ServiceType::getSort).orderByAsc(ServiceType::getId));
    }

    /** 服务区域树（一级市/区） */
    public List<ServiceArea> listAreas() {
        return areaMapper.selectList(Wrappers.<ServiceArea>lambdaQuery()
                .eq(ServiceArea::getStatus, 1).orderByAsc(ServiceArea::getLevel).orderByAsc(ServiceArea::getId));
    }

    /** 服务项目分页：按类型 + 关键词筛选，仅上架 */
    public PageResult<ServiceItem> pageItems(Long typeId, String keyword, int page, int size) {
        IPage<ServiceItem> result = itemMapper.selectPage(new Page<>(page, size),
                Wrappers.<ServiceItem>lambdaQuery()
                        .eq(ServiceItem::getStatus, 1)
                        .eq(typeId != null, ServiceItem::getTypeId, typeId)
                        .like(keyword != null && !keyword.isBlank(), ServiceItem::getName, keyword)
                        .orderByDesc(ServiceItem::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /** 服务详情（价格/时长/适用品种/注意事项） */
    public ServiceItem getItem(Long id) {
        ServiceItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.ITEM_NOT_FOUND);
        }
        return item;
    }

    /** 管理端全量查询 */
    public PageResult<ServiceItem> adminPageItems(Long typeId, String keyword, Integer status, int page, int size) {
        IPage<ServiceItem> result = itemMapper.selectPage(new Page<>(page, size),
                Wrappers.<ServiceItem>lambdaQuery()
                        .eq(typeId != null, ServiceItem::getTypeId, typeId)
                        .eq(status != null, ServiceItem::getStatus, status)
                        .like(keyword != null && !keyword.isBlank(), ServiceItem::getName, keyword)
                        .orderByDesc(ServiceItem::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /** 删除服务类型前校验是否被项目引用（PRD 7.1 规则） */
    public void deleteTypeChecked(Long typeId) {
        Long used = itemMapper.selectCount(Wrappers.<ServiceItem>lambdaQuery().eq(ServiceItem::getTypeId, typeId));
        if (used != null && used > 0) {
            throw new BizException(ErrorCode.DATA_IN_USE, "该服务类型下存在服务项目，无法删除");
        }
        typeMapper.deleteById(typeId);
    }
}
