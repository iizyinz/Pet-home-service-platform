package com.example.backend.service.staff;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.api.PageResult;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.example.backend.common.util.JsonUtil;
import com.example.backend.dto.staff.StaffDetailVO;
import com.example.backend.dto.staff.StaffListVO;
import com.example.backend.dto.staff.StaffProfileRequest;
import com.example.backend.entity.Favorite;
import com.example.backend.entity.ServiceArea;
import com.example.backend.entity.StaffItem;
import com.example.backend.entity.StaffProfile;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.FavoriteMapper;
import com.example.backend.mapper.ServiceAreaMapper;
import com.example.backend.mapper.StaffItemMapper;
import com.example.backend.mapper.StaffProfileMapper;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 服务人员：列表筛选（区域/擅长品种）、详情、收藏
 */
@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffProfileMapper profileMapper;
    private final SysUserMapper userMapper;
    private final ServiceAreaMapper areaMapper;
    private final FavoriteMapper favoriteMapper;
    private final StaffItemMapper staffItemMapper;

    /** 服务人员列表：仅认证通过且上架；支持区域筛选，按评分/接单量排序 */
    public PageResult<StaffListVO> pageStaffs(Long areaId, String breed, String keyword, int page, int size) {
        IPage<StaffProfile> result = profileMapper.selectPage(new Page<>(page, size),
                Wrappers.<StaffProfile>lambdaQuery()
                        .eq(StaffProfile::getCertStatus, 1)
                        .eq(StaffProfile::getStatus, 1)
                        .eq(areaId != null, StaffProfile::getAreaId, areaId)
                        .orderByDesc(StaffProfile::getAvgRating).orderByDesc(StaffProfile::getCompletedCount));

        List<StaffProfile> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), page, size);
        }
        List<StaffListVO> voList = toListVO(records);

        // 擅长品种 / 昵称关键词过滤（作用于当前分页结果，量级可控）
        if (breed != null && !breed.isBlank()) {
            voList = voList.stream()
                    .filter(v -> v.getGoodAtBreeds() != null && v.getGoodAtBreeds().contains(breed))
                    .collect(Collectors.toList());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            voList = voList.stream()
                    .filter(v -> v.getNickname() != null && v.getNickname().contains(kw))
                    .collect(Collectors.toList());
        }
        return PageResult.of(voList, result.getTotal(), page, size);
    }

    /** 服务人员详情（资质/简介/收藏状态/可服务项目） */
    public StaffDetailVO getStaff(Long currentUserId, Long staffId) {
        StaffProfile profile = profileMapper.selectById(staffId);
        SysUser user = profile == null ? null : userMapper.selectById(staffId);
        if (profile == null || user == null || !"STAFF".equals(user.getRole())) {
            throw new BizException(ErrorCode.STAFF_NOT_FOUND);
        }
        StaffDetailVO vo = new StaffDetailVO();
        fillBase(vo, profile, user);
        vo.setAreaName(areaName(profile.getAreaId()));
        vo.setIntro(profile.getIntro());
        vo.setCertImages(JsonUtil.toList(profile.getCertImages()));
        vo.setRejectReason(profile.getRejectReason());
        vo.setFavorited(currentUserId != null && isFavorite(currentUserId, staffId));
        return vo;
    }

    private String areaName(Long areaId) {
        if (areaId == null) {
            return null;
        }
        ServiceArea area = areaMapper.selectById(areaId);
        return area == null ? null : area.getName();
    }

    private boolean isFavorite(Long userId, Long staffId) {
        Long cnt = favoriteMapper.selectCount(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId).eq(Favorite::getStaffId, staffId));
        return cnt != null && cnt > 0;
    }

    private List<StaffListVO> toListVO(List<StaffProfile> profiles) {
        List<Long> ids = profiles.stream().map(StaffProfile::getUserId).toList();
        Map<Long, SysUser> userMap = userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
        Map<Long, String> areaMap = areaMapper.selectList(Wrappers.<ServiceArea>lambdaQuery()).stream()
                .collect(Collectors.toMap(ServiceArea::getId, ServiceArea::getName, (a, b) -> a));
        return profiles.stream().map(p -> {
            StaffListVO vo = new StaffListVO();
            fillBase(vo, p, userMap.get(p.getUserId()));
            vo.setAreaName(areaMap.get(p.getAreaId()));
            return vo;
        }).collect(Collectors.toList());
    }

    private void fillBase(StaffListVO vo, StaffProfile profile, SysUser user) {
        vo.setId(profile.getUserId());
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setPhone(UserService.maskPhone(user.getPhone()));
        }
        vo.setAreaId(profile.getAreaId());
        vo.setExperienceYears(profile.getExperienceYears());
        vo.setAvgRating(profile.getAvgRating());
        vo.setCompletedCount(profile.getCompletedCount());
        vo.setCertStatus(profile.getCertStatus());
        vo.setGoodAtBreeds(JsonUtil.toList(profile.getGoodAtBreeds()));
    }

    /** 收藏 / 取消收藏（幂等切换），返回切换后是否已收藏 */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long userId, Long staffId) {
        StaffProfile profile = profileMapper.selectById(staffId);
        if (profile == null) {
            throw new BizException(ErrorCode.STAFF_NOT_FOUND);
        }
        Favorite exist = favoriteMapper.selectOne(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId).eq(Favorite::getStaffId, staffId));
        if (exist != null) {
            favoriteMapper.deleteById(exist.getId());
            return false;
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setStaffId(staffId);
        favoriteMapper.insert(favorite);
        return true;
    }

    /** 我的收藏列表 */
    public List<StaffListVO> myFavorites(Long userId) {
        List<Favorite> favorites = favoriteMapper.selectList(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId).orderByDesc(Favorite::getId));
        if (favorites.isEmpty()) {
            return Collections.emptyList();
        }
        List<StaffProfile> profiles = profileMapper.selectBatchIds(
                favorites.stream().map(Favorite::getStaffId).toList());
        return toListVO(profiles);
    }

    /** 服务人员维护个人资料（简介/年限/擅长品种/区域/资质/可服务项目） */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void updateMyProfile(Long staffId, StaffProfileRequest req) {
        StaffProfile profile = profileMapper.selectById(staffId);
        if (profile == null) {
            throw new BizException(ErrorCode.STAFF_NOT_FOUND);
        }
        if (req.getIntro() != null) {
            profile.setIntro(req.getIntro());
        }
        if (req.getExperienceYears() != null) {
            profile.setExperienceYears(req.getExperienceYears());
        }
        if (req.getGoodAtBreeds() != null) {
            profile.setGoodAtBreeds(JsonUtil.toJson(req.getGoodAtBreeds()));
        }
        if (req.getAreaId() != null) {
            profile.setAreaId(req.getAreaId());
        }
        if (req.getCertImages() != null) {
            profile.setCertImages(JsonUtil.toJson(req.getCertImages()));
        }
        profileMapper.updateById(profile);

        if (req.getItemIds() != null) {
            staffItemMapper.delete(Wrappers.<StaffItem>lambdaQuery().eq(StaffItem::getStaffId, staffId));
            for (Long itemId : req.getItemIds().stream().distinct().toList()) {
                StaffItem si = new StaffItem();
                si.setStaffId(staffId);
                si.setItemId(itemId);
                staffItemMapper.insert(si);
            }
        }
    }

    /** 我的可服务项目ID */
    public List<Long> myItemIds(Long staffId) {
        return staffItemMapper.selectList(Wrappers.<StaffItem>lambdaQuery()
                .eq(StaffItem::getStaffId, staffId)).stream().map(StaffItem::getItemId).toList();
    }

    /** 我的服务人员资料（评分/累计接单/认证状态） */
    public StaffProfile myProfile(Long staffId) {
        StaffProfile profile = profileMapper.selectById(staffId);
        if (profile == null) {
            throw new BizException(ErrorCode.STAFF_NOT_FOUND);
        }
        return profile;
    }
}
