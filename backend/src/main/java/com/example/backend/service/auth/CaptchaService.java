package com.example.backend.service.auth;

import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 鍥惧舰楠岃瘉鐮侊細鐢熸垚 4 浣嶅瓧绗﹂獙璇佺爜鍥剧墖锛坆ase64锛夛紝
 * 鍐呭瓨瀛樺偍 key->code锛堝甫 TTL锛夛紝鐧诲綍鏃舵牎楠岋紝闃叉毚鍔涚牬瑙ｃ€?
 */
@Service
public class CaptchaService {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Value("${app.captcha.ttl-seconds}")
    private long ttlSeconds;

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 瀹氭湡娓呯悊杩囨湡楠岃瘉鐮侊紝闃插唴瀛樻硠婕?
        Thread cleaner = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60_000);
                    long now = System.currentTimeMillis();
                    store.entrySet().removeIf(e -> e.getValue().expireAt() < now);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "captcha-cleaner");
        cleaner.setDaemon(true);
        cleaner.start();
    }

    /** 鐢熸垚楠岃瘉鐮侊紝杩斿洖 {captchaKey, captchaImg(base64)} */
    public Map<String, String> generate() {
        String code = randomCode();
        String key = UUID.randomUUID().toString().replace("-", "");
        store.put(key, new Entry(code, System.currentTimeMillis() + ttlSeconds * 1000));
        try {
            return Map.of("captchaKey", key, "captchaImg", renderBase64(code));
        } catch (IOException e) {
            throw new BizException(ErrorCode.CAPTCHA_GENERATE_FAILED);
        }
    }

    /** 鏍￠獙骞朵竴娆℃€ф秷璐归獙璇佺爜 */
    public void verify(String key, String code) {
        if (key == null || code == null) {
            throw new BizException(ErrorCode.CAPTCHA_INVALID);
        }
        Entry entry = store.remove(key);
        if (entry == null || entry.expireAt() < System.currentTimeMillis()
                || !entry.code().equalsIgnoreCase(code.trim())) {
            throw new BizException(ErrorCode.CAPTCHA_INVALID);
        }
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(4);
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < 4; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String renderBase64(String code) throws IOException {
        int width = 120, height = 44;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(240, 250, 244));
            g.fillRect(0, 0, width, height);
            // 骞叉壈绾?
            java.security.SecureRandom random = new java.security.SecureRandom();
            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(120 + random.nextInt(120), 180 + random.nextInt(70), 140));
                g.drawLine(random.nextInt(width), random.nextInt(height), random.nextInt(width), random.nextInt(height));
            }
            // 瀛楃
            for (int i = 0; i < code.length(); i++) {
                g.setColor(new Color(20 + random.nextInt(60), 90 + random.nextInt(80), 60));
                g.setFont(new Font("Arial", Font.BOLD, 26 + random.nextInt(4)));
                g.drawString(String.valueOf(code.charAt(i)), 12 + i * 26, 32);
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    private record Entry(String code, long expireAt) {
    }
}

