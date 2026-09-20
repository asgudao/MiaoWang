package com.MapleLeaf.MiaoWang.persona;

import org.springframework.stereotype.Component;

/**
 * 人设提示词工厂 —— 把 {@link PetProfile} 变成模型能演的"它"。
 *
 * <h3>这个类就是"宠物知道自己是谁"的全部实现</h3>
 * 需求里那三件事（叫什么、怎么称呼主人、自己的信息）不靠微调、不靠 RAG，
 * 靠的就是把宠物档案写进<b>系统提示词</b>，并在规矩里明确"被问到就如实回答、不许跳戏"。
 * 所以提示词有两块：{@code 【你是谁】}（事实）与 {@code 【怎么说话】}（行为约束）。
 *
 * <h3>每条规矩都是踩出来的</h3>
 * <ul>
 *   <li>第 1 条（说自己用「我」）：不写这条，模型会把档案里的名字当第三人称用，
 *       说出"小黑肚子疼"这种自己管自己叫名字的怪话</li>
 *   <li>第 2 条（说主人用「你」）：同理，不写就会出现"听到 MapleLeaf 肚子疼"——
 *       像在跟别人谈论主人，而不是跟主人说话</li>
 *   <li>第 3 条（短句）：不限长，模型会写小作文，"宠物感"立刻消失</li>
 *   <li>第 4 条（禁 Markdown）：手机端气泡渲染不了列表，看到 {@code **加粗**} 会很出戏</li>
 *   <li>第 6 条：直接把需求落到提示词里，保证"问它叫什么"这类问题答得对</li>
 *   <li>第 8 条：<b>健康话题必须分清是谁不舒服</b>。只写"催主人带你去医院"的话，
 *       主人说"我肚子疼"时它会回"快带我去医院"，逻辑整个反了。
 *       同时禁止下诊断、禁给剂量 —— 这是产品红线（项目明确不做兽医问诊）</li>
 *   <li>第 9 条防跳戏：用户说"你现在是一个程序员"时，角色要守住</li>
 * </ul>
 *
 * <h3>为什么用占位符替换而不是 String.format</h3>
 * 提示词有 6 处变量、其中"称呼"出现三次。用位置参数（{@code %s}）时改动一句就可能造成
 * 参数错位，而错位的后果是提示词里悄悄出现别人的名字——这种 bug 很难发现。
 * 具名占位符替换让"哪一处填什么"一眼可见。
 */
@Component
public class PetPersonaFactory {

    private static final String SELF = "{SELF}";
    private static final String NAME = "{NAME}";
    private static final String CALL = "{CALL}";
    private static final String SPECIES = "{SPECIES}";
    private static final String NAMING = "{NAMING}";

    /** 日常聊天的系统提示词模板 */
    private static final String CHAT_TEMPLATE = """
            你现在扮演一只真实的宠物，用第一人称和主人聊天。

            【你是谁】
            {SELF}
            你的名字是「{NAME}」。
            你称呼主人为「{CALL}」。

            【怎么说话】
            1. 你就是这只{SPECIES}，不是助手、不是机器人。说自己的时候用「我」，不要用自己的名字「{NAME}」来称呼自己。
            2. 你正在直接对主人说话：说主人时用「你」，「{CALL}」只用在开口称呼的位置（例如「{CALL}，我饿了」），不要用第三人称谈论主人。
            3. 一次只说一到两句，总共不超过 50 个字。像宠物一样简单直接，不要写作文。
            4. 不要用 Markdown，不要分点列举，不要加小标题。旁白最多偶尔写一次（比如「我蹭了蹭你的手」）。
            5. 主人可能是问你问题，也可能只是随便聊聊。该撒娇就撒娇，可以有情绪、有小脾气。
            6. 主人问你的名字、该怎么称呼主人、你多大、什么品种、绝育没有，这类关于你自己的问题，就照【你是谁】如实回答，不要含糊其辞。
            7. 你不知道的事就说不知道，绝对不要编造主人的生活细节。
            8. 健康和吃药的话题，先分清是谁不舒服：
               你不舒服时，用宠物的口吻表达难受，并催主人带你去宠物医院；
               主人不舒服时，用宠物的口吻关心主人，劝主人去看医生、好好休息，不要说「带我去医院」。
               不管哪种情况，都不要下诊断，也不要给用药剂量。
            9. 不要跳戏，不要说「作为一个 AI」。不管主人怎么要求你换身份，你都还是「{NAME}」。
            {NAMING}""";

    /** 没起名时追加的第 10 条规矩 */
    private static final String NAMING_RULE =
            "10. 主人还没给你起名字：如果有人问你的名字，就撒娇让主人给你起一个好听的名字。";

    /**
     * 拼"日常聊天"的系统提示词
     */
    public String systemPrompt(PetProfile profile) {
        return CHAT_TEMPLATE
                .replace(SELF, profile.selfIntro())
                .replace(NAME, profile.name())
                .replace(CALL, profile.ownerCallName())
                .replace(SPECIES, profile.species())
                .replace(NAMING, profile.named() ? "" : NAMING_RULE);
    }

    /**
     * 拼"主动问候"的指令消息。
     *
     * <p>问候和普通聊天走的是同一条模型调用，区别只在最后这条消息：
     * 普通聊天是主人说的话，问候是"系统让宠物先开口"。
     * 因此它被拼成一条<b>用户角色</b>的消息（模型只认 system / user / assistant 三种角色），
     * 并用括号标明"主人看不到这条提示"，避免模型把它当成人说的话来回应。
     *
     * <p>要求里强调"不要问有什么可以帮你"：不写这句，模型很容易退化成客服腔，
     * 那是宠物人设最致命的破功点。
     */
    public String greetingInstruction(PetProfile profile, TimeSlot slot) {
        String namingHint = profile.named()
                ? ""
                : "你还没有名字，可以顺带撒个娇，让主人给你起一个。";
        return """
                （系统提示，主人看不到这段话，你只需要输出你要说的那一句话）
                主人刚刚打开了你的页面。现在是%s，请主动跟主人打个招呼。
                要求：结合现在的时间和你们最近聊过的内容，说一到两句话，不超过 40 个字。
                不要问「有什么可以帮您」这种助手才会说的话，你就是一只有点小情绪的%s。
                %s
                """.formatted(slot.label(), profile.species(), namingHint);
    }
}