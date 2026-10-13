package com.example.backend.service.admin;

import com.example.backend.common.util.JsonUtil;
import com.example.backend.entity.Review;
import com.example.backend.mapper.ReviewMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.seg.common.Term;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评价词云（PRD 7.3 / 8.7）：
 * 评价文本 -> HanLP 分词与词性标注 -> 过滤停用词与无效词性 -> 词频 Top80。
 * 分词组件异常时降级为按标点/空白切词，保证接口可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordCloudService {

    private final ReviewMapper reviewMapper;

    /** 停用词（语气词/虚词/通用词） */
    private static final Set<String> STOPWORDS = Set.of(
            "我们", "他们", "这个", "那个", "一个", "什么", "没有", "可以", "就是", "但是",
            "因为", "所以", "而且", "如果", "自己", "非常", "比较", "真的", "还是", "已经",
            "进行", "觉得", "感觉", "服务", "宠物", "师傅", "人员", "平台", "订单", "上门");

    /** 允许的词性：名词 n、动词 v、形容词 a（的/了等无效词性过滤） */
    private static boolean keepPos(String nature) {
        if (nature == null || nature.isEmpty()) {
            return false;
        }
        char c = nature.charAt(0);
        return c == 'n' || c == 'v' || c == 'a';
    }

    /** 词云：返回 [{name, value}] Top80 */
    public List<Map<String, Object>> wordCloud(int topN) {
        List<Review> reviews = reviewMapper.selectList(Wrappers.<Review>lambdaQuery()
                .eq(Review::getStatus, 1)
                .isNotNull(Review::getContent)
                .last("LIMIT 5000"));
        String joined = reviews.stream()
                .map(Review::getContent)
                .filter(s -> s != null && !s.isBlank())
                .reduce("", (a, b) -> a + " " + b);
        if (joined.isBlank()) {
            return List.of();
        }

        Map<String, Integer> freq = new HashMap<>();
        try {
            for (Term term : HanLP.segment(joined)) {
                String word = term.word;
                if (word.length() < 2 || word.length() > 8
                        || STOPWORDS.contains(word)
                        || !keepPos(term.nature == null ? null : term.nature.toString())) {
                    continue;
                }
                freq.merge(word, 1, Integer::sum);
            }
        } catch (Exception e) {
            log.warn("HanLP 分词失败，降级简单切词: {}", e.getMessage());
            freq.clear();
            for (String token : joined.split("[\\s，。！？、；：,.!?;:\"'（）()\\[\\]【】]+")) {
                if (token.length() >= 2 && token.length() <= 8 && !STOPWORDS.contains(token)) {
                    freq.merge(token, 1, Integer::sum);
                }
            }
        }

        int limit = topN <= 0 ? 80 : Math.min(topN, 80);
        return freq.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(limit)
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", e.getKey());
                    item.put("value", e.getValue());
                    return item;
                })
                .toList();
    }
}
