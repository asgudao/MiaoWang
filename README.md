# 🐱🐶 喵汪 (MiaoWang)

面向猫狗主人的宠物养护助手，帮助你科学管理萌宠的日常生活。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | uni-app x (uvue) + Android 原生渲染 |
| 后端 | Spring Boot 3.1.5 + MyBatis-Plus |
| 数据库 | MySQL |
| 认证 | JWT |

## 项目结构

```
MiaoWang/
├── back-project/          # Spring Boot 后端
│   └── src/main/java/com/mapleleaf/petapp/
│       ├── module/
│       │   ├── user/           # 用户认证
│       │   ├── pet/            # 宠物 & 品种管理
│       │   ├── knowledge/      # 知识库
│       │   ├── reminder/       # 护理提醒
│       │   └── subscription/   # 会员订阅
│       └── config/             # Security / JWT 配置
├── front-project/         # uni-app x 前端
│   └── pages/
│       ├── index/              # 首页（3D 小猫）
│       ├── knowledge/          # 知识库（列表 + 详情 + 收藏）
│       ├── pets/               # 萌宠管理（列表 + 添加）
│       ├── reminder/           # 护理日历
│       ├── favorites/          # 我的收藏
│       ├── subscription/       # 会员订阅
│       ├── mine/               # 我的
│       └── about/              # 关于
└── deploy.ps1             # 一键部署脚本
```

## 功能

- **🐾 萌宠管理** — 添加猫咪/狗狗，记录品种、年龄、体重
- **📅 护理日历** — 疫苗、驱虫、洗澡等提醒，状态颜色自动变化
- **📚 知识库** — 分类浏览 + 搜索养宠知识文章
- **⭐ 收藏夹** — 收藏有用文章，本地存储
- **🐱 3D 首页** — Three.js 渲染的 3D 小猫模型
- **👤 个人中心** — 会员订阅、关于页面

## 快速开始

### 1. 启动后端

```bash
cd back-project
# 需要 JDK 17+ 和 Maven
mvn spring-boot:run -s settings-temp.xml
```

后端运行在 `http://localhost:8080`

### 2. 运行前端

用 HBuilderX 打开 `front-project` 目录，点击 **运行 → 运行到手机或模拟器 → Android App 基座**

### 3. 命令行部署（备用）

```powershell
# 编译 + 推送到已连接的 Android 手机
.\deploy.ps1
```

## API 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 手机号登录 |
| POST | `/api/auth/register` | 手机号注册 |
| GET | `/api/pets` | 获取宠物列表 |
| POST | `/api/pets` | 添加宠物 |
| GET | `/api/breeds` | 获取品种列表 |
| GET | `/api/knowledge` | 知识库列表 |
| GET | `/api/knowledge/:id` | 文章详情 |
| GET | `/api/knowledge/categories` | 分类列表 |
| GET | `/api/knowledge/search` | 搜索文章 |
| GET | `/api/reminders` | 护理提醒列表 |
| POST | `/api/reminders` | 添加提醒 |
| GET | `/api/subscription/plans` | 会员套餐 |
| POST | `/api/subscription/order` | 创建订单 |
| GET | `/api/user/profile` | 用户信息 |
