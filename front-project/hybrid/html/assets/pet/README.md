# 宠物动作素材

`idle.json` / `happy.json` / `eat.json` / `sleep.json` / `play.json` 为项目自制的 Lottie 矢量动画，由 `front-project/build-pet-lottie.js` 生成，可在该脚本中统一调整配色与动作关键帧。

- 风格：纯小火人风（暖色圆润角色）
- 主色：`#FF8C42` / `#FFD166` / `#FF6B5F` / `#FFB088`
- 帧率：30fps；画布：512x512
- 动作：待机（呼吸+火焰跳动+眨眼）、开心（跳跃+挥臂）、吃饭（咀嚼+食物）、睡觉（闭眼+呼吸+Z）、玩耍（旋转+弹跳）
- 许可：项目自有素材，随项目仓库使用

修改后重新生成：`node build-pet-lottie.js`

## 首页内嵌

运行时为了避免 WebView 的 file:// CORS 拦截，动画数据已内嵌到 pet-home.html。修改 JSON 后执行：
ode build-pet-html.js 重新生成。
