package com.MapleLeaf.MiaoWang.persona;

/**
 * 宠物人设档案 —— 拼提示词所需的全部"关于我"的信息，且都已加工成可直接念出来的说法。
 *
 * <h3>为什么不让提示词模板直接吃 DTO</h3>
 * {@code PetFeignRespDTO} 里是 {@code gender=0/1}、{@code birth=2020-05-01}、
 * {@code sterilizedState=0/1} 这种"库里的形状"，模型需要的是"一只三岁的橘猫，是母的，已经绝育"。
 * 把"翻译成人话"这一步收在一个地方，提示词模板才读得懂、也才改得动
 * （以后要加"毛色""体重"，只改 {@link PetProfileLoader}，提示词一行都不用动）。
 *
 * @param pid            宠物ID
 * @param name           宠物名（为空时已填「未命名」，见 {@code named}）
 * @param named          主人是否真的给它起过名字（没起名时人设里会请主人起一个）
 * @param species        大类（猫/狗/…，库中非空）
 * @param breed          品种描述（如「橘猫」；未知时为空串）
 * @param ownerCallName  它对主人的称呼（如「主人」；未设置时已填兜底）
 * @param ageText        年龄说法（如「3岁」「5个月」；生日未知时为空串）
 * @param genderText     性别字（「母」/「公」；未知时为空串）
 * @param sterilizedText 绝育说法（如「已经绝育」；未知时为空串）
 */
public record PetProfile(
        Long pid,
        String name,
        boolean named,
        String species,
        String breed,
        String ownerCallName,
        String ageText,
        String genderText,
        String sterilizedText) {

    /**
     * 拼成"我是谁"这一句，直接写进系统提示词。
     *
     * <pre>
     *   有名字：小黑，一只 3 岁的橘猫，是母的，已经绝育
     *   没名字：一只 5 个月的猫（主人还没给我起名字）
     * </pre>
     * 未知的信息会被自然跳过，不会留下"一只 null 的 null"这种破句 ——
     * 提示词里出现坏句子的代价是模型跟着一起胡说。
     */
    public String selfIntro() {
        StringBuilder sb = new StringBuilder();
        if (named) {
            sb.append(name).append("，");
        }
        sb.append("一只");
        if (!ageText.isEmpty()) {
            sb.append(ageText).append("的");
        }
        if (!breed.isEmpty()) {
            sb.append(breed);
        }
        sb.append(species);
        if (!genderText.isEmpty()) {
            sb.append("，是").append(genderText).append("的");
        }
        if (!sterilizedText.isEmpty()) {
            sb.append("，").append(sterilizedText);
        }
        if (!named) {
            sb.append("（主人还没给我起名字）");
        }
        return sb.toString();
    }
}