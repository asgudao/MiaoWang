/*
 Navicat Premium Dump SQL

 Source Server         : 192.168.137.128-3306-mysql
 Source Server Type    : MySQL
 Source Server Version : 80403 (8.4.3)
 Source Host           : 192.168.137.128:3306
 Source Schema         : MiaoWang

 Target Server Type    : MySQL
 Target Server Version : 80403 (8.4.3)
 File Encoding         : 65001

 Date: 14/09/2026 13:50:41
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for miaowang_pet
-- ----------------------------
DROP TABLE IF EXISTS `miaowang_pet`;
CREATE TABLE `miaowang_pet`  (
  `pid` int NOT NULL AUTO_INCREMENT,
  `uid` int NOT NULL,
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `p_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `owner_call_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `age` int NOT NULL,
  `deletion_time` bigint NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`pid`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of miaowang_pet
-- ----------------------------
INSERT INTO `miaowang_pet` VALUES (1, 1, 'MapleLeaf', '小黑', 'MapleLeaf', 1, NULL, '2026-09-13 16:26:51', '2026-09-13 16:26:51', 0);
INSERT INTO `miaowang_pet` VALUES (2, 1, 'MapleLeaf', NULL, 'MapleLeaf', 2, NULL, '2026-09-13 22:02:33', '2026-09-13 22:02:33', 0);
INSERT INTO `miaowang_pet` VALUES (3, 1, 'MapleLeaf', '小白', '主人', 4, 1789353065450, '2026-09-14 10:00:23', '2026-09-14 10:00:23', 1);

-- ----------------------------
-- Table structure for miaowang_user
-- ----------------------------
DROP TABLE IF EXISTS `miaowang_user`;
CREATE TABLE `miaowang_user`  (
  `uid` int NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户名',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码',
  `real_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '真实姓名',
  `owner_call_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '主人' COMMENT '宠物对用户的称呼',
  `phone` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号码',
  `mail` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱地址',
  `deletion_time` bigint NULL DEFAULT NULL COMMENT '注销时间戳',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` tinyint NOT NULL DEFAULT 0 COMMENT '删除标识：未删除-0，已删除-1',
  PRIMARY KEY (`uid`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of miaowang_user
-- ----------------------------
INSERT INTO `miaowang_user` VALUES (1, 'MapleLeaf', '128ve980', 'Acer', '主人', '13653584645', 'MapleLeaf000@163.com', NULL, '2026-09-11 14:39:00', '2026-09-11 19:37:30', 0);
INSERT INTO `miaowang_user` VALUES (2, 'MapleLeaf1', '1234567', 'gyf', '主人', '13677778888', '13677778888@163.com', NULL, '2026-09-11 16:43:40', '2026-09-11 19:37:33', 0);

SET FOREIGN_KEY_CHECKS = 1;
