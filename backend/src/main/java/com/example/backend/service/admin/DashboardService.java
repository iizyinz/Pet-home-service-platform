package com.example.backend.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.entity.Review;
import com.example.backend.entity.ServiceOrder;
import com.example.backend.mapper.PetMapper;
import com.example.backend.mapper.ReviewMapper;
import com.example.backend.mapper.ServiceOrderMapper;
import com.example.backend.mapper.StaffProfileMapper;
import com.example.backend.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据看板（PRD 7.3）：核心指标卡、订单趋势、状态分布、排行、评分分布。
 * 聚合全部走 SQL；如需更强性能可加缓存/物化视图。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ServiceOrderMapper orderMapper;
    private final SysUserMapper userMapper;
    private final PetMapper petMapper;
    private final StaffProfileMapper profileMapper;
    private final ReviewMapper reviewMapper;

    /** 指标卡：累计/今日订单、平台收入、用户/服务师/宠物数、满意率 */
    public Map<String, Object> overview() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalOrders", orderMapper.countAll());
        data.put("todayOrders", orderMapper.countToday());
        data.put("totalRevenue", orderMapper.sumCompletedRevenue());
        data.put("todayRevenue", orderMapper.sumCompletedRevenueToday());
        data.put("userCount", userMapper.countByRole("USER"));
        data.put("staffCount", userMapper.countByRole("STAFF"));
        data.put("petCount", petMapper.countAll());
        long total = countReviews();
        long good = countGoodReviews();
        data.put("satisfiedRate", total == 0 ? 100.0 : Math.round(good * 1000.0 / total) / 10.0);
        data.put("totalReviews", total);
        return data;
    }

    /**
     * 近 N 天订单趋势（折线图）
     * <p>
     * 注意：不能把 "DATE_SUB(CURDATE(), ...)" 这样的 SQL 表达式当参数传给
     * QueryWrapper#ge —— 它会被绑定成占位符字符串，MySQL 无法隐式转换成 DATETIME
     * （报 Incorrect DATETIME value）。这里直接在 Java 侧算出日期。
     */
    public List<Map<String, Object>> orderTrend(int days) {
        int window = Math.min(Math.max(days, 1), 365);   // 防御非法/超大参数
        LocalDate since = LocalDate.now().minusDays(window);
        QueryWrapper<ServiceOrder> wrapper = new QueryWrapper<>();
        wrapper.select("DATE(created_at) AS day", "COUNT(*) AS cnt")
                .ge("created_at", since.atStartOfDay())
                .groupBy("DATE(created_at)")
                .orderByAsc("DATE(created_at)");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : orderMapper.selectMaps(wrapper)) {
            Map<String, Object> item = new HashMap<>();
            Object day = row.get("day");
            item.put("date", day == null ? "" : String.valueOf(day).substring(0, Math.min(10, String.valueOf(day).length())));
            item.put("count", row.get("cnt"));
            result.add(item);
        }
        return result;
    }

    /** 订单状态分布（饼图） */
    public List<Map<String, Object>> statusDist() {
        QueryWrapper<ServiceOrder> wrapper = new QueryWrapper<>();
        wrapper.select("status", "COUNT(*) AS cnt").groupBy("status");
        return orderMapper.selectMaps(wrapper).stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("status", row.get("status"));
            item.put("count", row.get("cnt"));
            return item;
        }).toList();
    }

    /** 服务项目排行（按已完成订单数 Top10） */
    public List<Map<String, Object>> itemRank() {
        QueryWrapper<ServiceOrder> wrapper = new QueryWrapper<>();
        wrapper.select("item_name AS name", "COUNT(*) AS cnt")
                .eq("status", "COMPLETED")
                .groupBy("item_name")
                .orderByDesc("cnt")
                .last("LIMIT 10");
        return orderMapper.selectMaps(wrapper).stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("name", row.get("name"));
            item.put("count", row.get("cnt"));
            return item;
        }).toList();
    }

    /** 服务人员接单排行（按已完成订单数 Top10） */
    public List<Map<String, Object>> staffRank() {
        QueryWrapper<ServiceOrder> wrapper = new QueryWrapper<>();
        wrapper.select("staff_id AS staffId", "COUNT(*) AS cnt")
                .eq("status", "COMPLETED")
                .isNotNull("staff_id")
                .groupBy("staff_id")
                .orderByDesc("cnt")
                .last("LIMIT 10");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : orderMapper.selectMaps(wrapper)) {
            Map<String, Object> item = new HashMap<>();
            Object staffId = row.get("staffId");
            item.put("staffId", staffId);
            item.put("count", row.get("cnt"));
            try {
                var user = userMapper.selectById(Long.valueOf(String.valueOf(staffId)));
                item.put("name", user == null ? staffId : user.getNickname());
            } catch (Exception e) {
                item.put("name", staffId);
            }
            result.add(item);
        }
        return result;
    }

    /** 评分分布（各星级数量与占比） */
    public List<Map<String, Object>> ratingDist() {
        QueryWrapper<Review> wrapper = new QueryWrapper<>();
        wrapper.select("rating", "COUNT(*) AS cnt").eq("status", 1).groupBy("rating").orderByAsc("rating");
        List<Map<String, Object>> rows = reviewMapper.selectMaps(wrapper);
        long total = 0;
        for (Map<String, Object> row : rows) {
            total += Long.parseLong(String.valueOf(row.get("cnt")));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int rating = 1; rating <= 5; rating++) {
            long cnt = 0;
            for (Map<String, Object> row : rows) {
                if (Integer.parseInt(String.valueOf(row.get("rating"))) == rating) {
                    cnt = Long.parseLong(String.valueOf(row.get("cnt")));
                }
            }
            Map<String, Object> item = new HashMap<>();
            item.put("rating", rating);
            item.put("count", cnt);
            item.put("percent", total == 0 ? 0.0 : Math.round(cnt * 1000.0 / total) / 10.0);
            result.add(item);
        }
        return result;
    }

    private long countReviews() {
        Long cnt = reviewMapper.selectCount(new QueryWrapper<Review>().eq("status", 1));
        return cnt == null ? 0 : cnt;
    }

    private long countGoodReviews() {
        Long cnt = reviewMapper.selectCount(new QueryWrapper<Review>().eq("status", 1).ge("rating", 4));
        return cnt == null ? 0 : cnt;
    }
}

