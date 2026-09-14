-- =============================================================
-- 猫 日常养护 知识碎片（LLM 生成，待人工审校）
-- species=1, category=care, content_type=care_schedule
-- 批次: LLM-CAT-CARE  source=LLM  status=1(待审)
-- 审校通过后发布:
--   UPDATE knowledge_fragment SET status=2 WHERE batch_no='LLM-CAT-CARE';
-- 结构镜像狗手册《日常养护注意事项》25 条，供 reminder 模块消费
-- =============================================================
SET NAMES utf8mb4;

INSERT INTO `knowledge_fragment` (`category_code`,`species`,`title`,`content`,`remark`,`content_type`,`tags`,`source`,`batch_no`,`status`,`create_time`,`update_time`,`del_flag`) VALUES
('care',1,'疫苗接种','幼猫 8/12/16 周龄接种猫三联（防猫瘟、猫鼻支、杯状病毒），之后每年加强 1 次','狂犬疫苗按当地规定接种','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'体内驱虫','幼猫 6 周龄起每月 1 次至 6 月龄，之后每 3 个月 1 次','生食或经常外出的猫可增加到每月 1 次；驱虫药按体重选择','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'体外驱虫','外出或常接触其他动物的猫每月 1 次，纯室内猫每 1-3 个月 1 次','滴剂/口服均可，注意与体内驱虫药区分','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'绝育','建议 5-6 月龄（体重达标后）咨询兽医','可降低乱尿、叫春、子宫蓄脓等风险','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'互动运动','每天用逗猫棒等玩具互动 15-30 分钟，模拟狩猎','互动不足易肥胖、抓咬家具、过度舔毛','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'垂直空间','提供猫爬架/跳台，满足攀爬天性','猫喜欢高处，上下攀爬兼磨爪','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'洗澡','一般 1-3 个月 1 次即可，猫会自我清洁','用宠物专用香波；频繁洗易应激伤皮肤；长毛/皮肤病遵医嘱','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'梳毛','短毛猫每周 1-2 次，长毛猫每天 1 次','换毛季加大频率，减少毛球症','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'剪指甲','每 1-2 周 1 次，只剪血线以上的透明部分','从小适应；抗拒可求助宠物店','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'刷牙','每周 2-3 次，用宠物专用牙膏','猫牙周病高发，从小适应更配合','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'洁耳','每月检查 1 次，有耳垢异味用宠物洗耳液','垂耳猫更易发炎，异常就医','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'眼周清洁','有泪痕时用湿棉球轻擦眼周','持续流泪红肿需排查鼻泪管或感染','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'饮水','全天提供干净流动水（宠物饮水机更佳），每日更换','公猫尤其要多喝水，预防下尿路疾病','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'喂食','成猫每天 2 顿定时定量，幼猫 3-4 顿少食多餐','以营养均衡主粮为主，避免长期自助餐导致肥胖','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'体重管理','每月称重 1 次，波动超过 10% 及时就医','肥胖显著增加糖尿病、脂肪肝、关节病风险','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'夏季防暑','保持通风凉爽，避免正午阳光直射','猫汗腺少中暑风险高，绝不要把猫留在封闭车内','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'冬季保暖','提供离地保暖睡窝','幼猫老年猫更怕冷，注意取暖器烫伤','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'化毛管理','换毛季每周喂 1-2 次化毛膏或猫草，长毛猫更频繁','吐毛球超过每月 1-2 次需就医排查','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'定期体检','成年猫每年 1 次，老年猫（7 岁+）每半年 1 次','重点查肾、甲状腺、牙、心脏','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'猫砂盆管理','每猫至少 1 个猫砂盆，另加 1 个备用','每天铲 1-2 次，每周彻底更换清洗','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'抓板补给','提供猫抓板/柱，损坏及时更换','满足磨爪天性，保护家具','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'居家安全','高层封窗，收好百合、绿萝等植物、药品和线绳玩具','百合对猫剧毒；坠楼是高层养猫第一死因','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'老年猫护理','软垫睡窝、防滑地面、低脂易消化粮、提高体检频率','关注肾衰、甲亢、关节炎','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'新猫到家','先隔离 7-14 天观察健康与应激，再逐步接触原住民','减少应激与传染病交叉，给足安全感','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0),
('care',1,'异常就医信号','精神萎靡+拒食超 24 小时、反复呕吐腹泻、尿闭/尿血、呼吸困难、抽搐','猫忍耐力极强，症状明显往往已严重，立即就医','care_schedule',NULL,'LLM','LLM-CAT-CARE',1,NOW(),NOW(),0);