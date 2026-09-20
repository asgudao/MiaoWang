package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

/**
 * 打开宠物页面时的自动问候请求
 *
 * <p>请求体可以为空 —— 它表达的其实是"我来了"这个动作，而不是一份数据。
 * 之所以用 POST 而不是 GET：它会（在缓存未命中时）产生一次真实的大模型调用，
 * 有副作用、也不该被浏览器/中间层当幂等读缓存起来。
 */
@Data
public class AiPetGreetReqDTO {

    /**
     * 宠物ID；不传 = 用当前用户的第一只宠物
     */
    private Long pid;
}