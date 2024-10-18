package run.ikaros.plugin.mikan.utils;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StringMatchingUtilsTest {

    static Map<String, String> map = new HashMap<>();

    static {
        map.put("[Up to 21°C] 最狂辅助职业【话术士】世界最强战团听我号令 / Saikyou no Shienshoku 'Wajutsushi' - 03 (B-Global 1920x1080 HEVC AAC MKV)",
                "[Up to 21°C] 最狂辅助职业【话术士】世界最强战团听我号令 / Saikyou no Shienshoku 'Wajutsushi' - 03 (CR 1920x1080 AVC AAC MKV)");
        map.put("[喵萌奶茶屋&LoliHouse] 物语系列 / Monogatari Series: Off & Monster Season - 13 忍物语 [WebRip 1080p HEVC-10bit AAC ASSx2][简繁日内封字幕]",
                "[喵萌奶茶屋&LoliHouse] 物语系列 / Monogatari Series: Off & Monster Season - 13 忍物语 [WebRip 1080p HEVC-10bit AAC ASSx2][简繁日内封字幕]");
        map.put("[Up to 21°C] 重启人生的千金小姐正在攻略龙帝陛下 / Yarinaoshi Reijou wa Ryuutei Heika wo Kouryakuchuu - 02 (ABEMA 1920x1080 AVC AAC MP4)",
                "[Up to 21°C] Yarinaoshi Reijou wa Ryuutei Heika wo Kouryakuchuu - 02 (ABEMA 1920x1080 AVC AAC MP4) [52A40BD5].mp4");
    }

    // @Test
    void isSimilar() {
        for(Map.Entry<String, String> entry : map.entrySet()) {
            assertTrue(StringMatchingUtils.isSimilar(entry.getKey(), entry.getValue(), 0.8));
        }
    }
}