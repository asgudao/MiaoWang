package com.MapleLeaf.MiaoWang.enums;


/**
 * 额度业务类型：决定扣的是哪个维度的每日次数
 */
public enum VipBizTypeEnum {

    /**
     * RAG 知识问答（调 MiaoWang-ai-knowledge 的问答能力前先扣这个额度）
     */
    RAG("rag", "RAG 问答"),

    /**
     * 与宠物聊天（调 MiaoWang-ai-pet 的对话能力前先扣这个额度）
     */
    CHAT("chat", "宠物聊天"),
    ;

    private final String biz;

    private final String desc;

    VipBizTypeEnum(String biz, String desc) {
        this.biz = biz;
        this.desc = desc;
    }

    public String getBiz() {
        return biz;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 解析业务类型，不认识返回 null（由调用方决定报错方式）
     */
    public static VipBizTypeEnum of(String biz) {
        for (VipBizTypeEnum item : values()) {
            if (item.biz.equalsIgnoreCase(biz)) {
                return item;
            }
        }
        return null;
    }
}