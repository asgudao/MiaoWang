# Knowledge base content summary

Normalized from the dog & cat handbooks, plus LLM-generated cat care routines (25 rows). Total fragments: **434**.

## species=1 (猫)

### 饮食安全 (51 rows)  types: guidex33 / warningx18

- `guide` **煮熟的鸡胸肉（无骨、无调味）** - 高蛋白低脂，是最好的加餐
- `guide` **煮熟的牛肉/鸭肉（瘦）** - 注意去骨去筋膜
- `guide` **煮熟的蛋黄（每周 1-2 个）** - 富含卵磷脂，美毛；蛋白易致敏，少给
- `guide` **熟鸡肝（少量、偶尔）** - 维生素 A 丰富；维 A 过多会中毒，少量
- `guide` **南瓜泥（蒸熟）** - 纤维丰富，便秘/腹泻都可辅助调节
- `guide` **胡萝卜、西兰花（煮熟）** - 提供维生素，过量易胀气
- ... and 45 more rows (see seed SQL)

### 行为解读 (103 rows)  types: guidex103

- `guide` **尾巴竖直、尾尖微微向前弯** - 心情愉悦、友好问候、表示信任
- `guide` **尾巴大幅左右甩动（拍打地面）** - 烦躁、不耐烦、被打扰
- `guide` **尾巴尖轻微抖动** - 专注、好奇、轻度兴奋
- `guide` **尾巴下垂、夹在两腿间** - 害怕、紧张、示弱、顺从
- `guide` **尾巴膨大（炸毛）、呈刷状** - 恐惧、防御、威吓
- `guide` **尾巴贴地平伸、低摆** - 专注跟踪猎物、捕猎前姿态
- ... and 97 more rows (see seed SQL)

### 日常养护 (25 rows)  types: care_schedulex25

- `care_schedule` **疫苗接种** - 幼猫 8/12/16 周龄接种猫三联（防猫瘟、猫鼻支、杯状病毒），之后每年加强 1 次
- `care_schedule` **体内驱虫** - 幼猫 6 周龄起每月 1 次至 6 月龄，之后每 3 个月 1 次
- `care_schedule` **体外驱虫** - 外出或常接触其他动物的猫每月 1 次，纯室内猫每 1-3 个月 1 次
- `care_schedule` **绝育** - 建议 5-6 月龄（体重达标后）咨询兽医
- `care_schedule` **互动运动** - 每天用逗猫棒等玩具互动 15-30 分钟，模拟狩猎
- `care_schedule` **垂直空间** - 提供猫爬架/跳台，满足攀爬天性
- ... and 19 more rows (see seed SQL)

### 健康自查 (52 rows)  types: medicalx52

- `medical` **食欲突然大增** - 糖尿病、甲亢、肠道寄生虫
- `medical` **食欲突然大减或完全不吃** - 口腔疾病、胃肠炎、肝肾问题、应激、严重感染
- `medical` **只对特定食物有兴趣（挑食加剧）** - 口腔疼痛、消化不良
- `medical` **饮水量突然大增** - 糖尿病、肾衰、甲亢
- `medical` **饮水量明显减少** - 脱水、口腔疼痛、全身不适
- `medical` **持续呕吐（区分吐毛球）** - 胃肠炎、毛球症、异物、肾衰、胰腺炎
- ... and 46 more rows (see seed SQL)

## species=2 (狗)

### 饮食安全 (40 rows)  types: guidex17 / warningx23

- `guide` **熟鸡胸肉** - 高蛋白低脂，去骨去皮、不加调料
- `guide` **熟牛肉/瘦猪肉** - 优质蛋白，切小块防噎
- `guide` **熟三文鱼/鳕鱼** - 优质蛋白+Omega-3，必须去净鱼刺
- `guide` **熟鸡蛋黄** - 卵磷脂美毛，每周 2-3 个即可
- `guide` **胡萝卜** - 煮熟更易吸收，护眼美毛
- `guide` **南瓜** - 助消化，缓解便秘和软便
- ... and 34 more rows (see seed SQL)

### 行为解读 (92 rows)  types: guidex92

- `guide` **汪汪！汪汪！（短促、连续）** - 警告、警觉，提醒有异常
- `guide` **汪——汪——（低沉、缓慢）** - 威胁、警告对方退让
- `guide` **汪汪汪汪！（高频、急促）** - 兴奋、欢迎、想玩耍
- `guide` **汪！（单声、短促）** - 好奇、疑问、提醒
- `guide` **嗷呜～（仰头长嚎）** - 孤独、回应同类、被高音触发
- `guide` **呜～呜～（嘤嘤哼唧）** - 焦虑、委屈、有需求
- ... and 86 more rows (see seed SQL)

### 日常养护 (25 rows)  types: care_schedulex25

- `care_schedule` **疫苗接种** - 幼犬 6-8 周龄起接种联苗（防细小、犬瘟等），共约 3 针，之后每...
- `care_schedule` **体内驱虫** - 幼犬每月 1 次至 6 月龄，之后每 3 个月 1 次
- `care_schedule` **体外驱虫** - 跳蚤、蜱虫高发季每月 1 次
- `care_schedule` **绝育** - 建议 6-8 月龄左右咨询兽医
- `care_schedule` **遛狗** - 每天 1-2 次，每次 30-60 分钟
- `care_schedule` **运动量** - 大型犬每天需 1-2 小时活动，小型犬略少
- ... and 19 more rows (see seed SQL)

### 健康自查 (46 rows)  types: medicalx46

- `medical` **一直呕吐** - 嗓子里卡了异物（骨头渣、玩具碎片）；吃得太快；吃了变质食物；晕车
- `medical` **吐黄色泡沫** - 空腹时间太长、胃里没东西；晕车；饿过头
- `medical` **干呕但吐不出东西** - 喉咙卡了东西；吃太快噎到；想吐但胃里没食物
- `medical` **拉稀（腹泻）** - 吃坏东西；突然换粮不适应；着凉；偷吃油腻食物；紧张
- `medical` **便便带血** - 吞了骨头渣等尖锐物划伤肠道；便秘用力过猛
- `medical` **便秘、排便困难** - 喝水太少；运动不足；吃太多骨头；吞了毛发
- ... and 40 more rows (see seed SQL)

## Source statistics

| species | sheet | rows |
|---|---|---|
| 2 | 狗的叫声 | 42 |
| 2 | 狗的肢体动作 | 50 |
| 2 | 饮食注意 | 40 |
| 2 | 日常养护注意事项 | 25 |
| 2 | 症状自查表 | 46 |
| 1 | 肢体动作 | 52 |
| 1 | 叫声含义 | 51 |
| 1 | 病症预警 | 52 |
| 1 | 饮食注意 | 51 |

> The 25 cat 日常养护 rows are LLM-generated (source=LLM, batch=LLM-CAT-CARE, status=1 pending review), mirroring the dog care sheet. Publish with: `UPDATE knowledge_fragment SET status=2 WHERE batch_no='LLM-CAT-CARE';`
