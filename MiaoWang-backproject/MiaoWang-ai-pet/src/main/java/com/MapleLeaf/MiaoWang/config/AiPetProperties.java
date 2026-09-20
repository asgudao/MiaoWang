package com.MapleLeaf.MiaoWang.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 电子宠物对话的可调参数（前缀 {@code miaowang.ai-pet}）。
 *
 * <p>为什么单独一个配置类而不是写死常量：这几项都是"上线后按实际表现调"的旋钮 ——
 * 问候太频繁、历史太长导致 token 费钱、有人灌长文探成本，都能在 Nacos 上改完即时生效（无需重启）。
 * 代码里都给了默认值，Nacos 不配也能跑。
 */
@Data
@ConfigurationProperties(prefix = "miaowang.ai-pet")
public class AiPetProperties {

    /**
     * 自动问候总开关。默认开：打开首页就有宠物说的话，是这个模块最主要的"活着"的感觉。
     */
    private Boolean greetingEnabled = true;

    /**
     * 带进提示词的历史轮数上限（1 轮 = 主人一句 + 宠物一句）
     */
    private Integer historyTurns = 6;

    /**
     * 历史字符预算：从最近往回累计，超过就丢更早的消息。
     * 这是最朴素的 token 预算裁剪 —— 比"按条数截断"更贴近真实成本。
     */
    private Integer historyCharBudget = 2000;

    /**
     * 单条提问长度上限（默认 500 字）。挡住"一次贴一整本书"这种把单次成本打爆的用法。
     */
    private Integer maxQuestionLength = 500;
}