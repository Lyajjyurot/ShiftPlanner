# 简表排班 (ShiftPlanner)

一个**完全离线**的安卓排班工具。数据只保存在本机，应用不申请网络权限，不会上传任何内容。
<img width="365" height="649" alt="屏幕截图 2026-10-14 141030" src="https://github.com/user-attachments/assets/59b33bab-160a-4184-8fa2-c6e9f7b095fe" />

## 功能

- **日历排班**：月视图日历（含农历），每天最多 3 个班次时段；点击日期即可查看/编辑当日班次与待办。
  - <img width="357" height="654" alt="image" src="https://github.com/user-attachments/assets/809d9da8-723e-49a5-8b05-d4269b046e6f" />
- **标签体系**：班次标签（名称、起止时间、颜色、提醒时刻）与待办标签（名称、截止时长、颜色、提醒规则）分开管理。
  - <img width="367" height="650" alt="屏幕截图 2026-10-14 141203" src="https://github.com/user-attachments/assets/7b077037-44cf-4399-991d-b89ccaf69d4f" />
- **待办提醒规则**
  - 截止时长以「天」为单位：可选择截止日当天几点提醒。
  - 截止时长以「小时」为单位：可设置截止时刻前 N 小时 M 分钟提前提醒。
  
    -<img width="366" height="650" alt="屏幕截图 2026-10-14 141232" src="https://github.com/user-attachments/assets/405388e3-f578-4982-b358-4cbac682ceec" />
  
- **批量排班**：自动排班（起止日期 + 间隔 + 冲突处理）与区间重排，生成前可预览将写入/冲突的日期。
- **混合选择**：普通排班、自动排班、点击日期排班三种方式均可一次混选班次与待办标签，最多三个。
  - <img width="368" height="648" alt="屏幕截图 2026-10-14 141353" src="https://github.com/user-attachments/assets/2ff59f76-0124-4dd7-98a7-be1a524fdd73" /><img width="365" height="649" alt="屏幕截图 2026-10-14 141344" src="https://github.com/user-attachments/assets/a084ca34-280a-4ef0-bab7-4b7230ac5307" /><img width="364" height="648" alt="屏幕截图 2026-10-14 141251" src="https://github.com/user-attachments/assets/703c8de1-262c-42a1-9dc0-8c1375dd21e2" />
- **统计**：按月统计值班天数、值班时长（按标签分色的饼图）、日均时长与休息天数。
  - <img width="362" height="649" alt="屏幕截图 2026-10-14 141306" src="https://github.com/user-attachments/assets/41dc5b39-e88f-4989-9dbd-21f9cbf86ac3" />
- **系统日历提醒**：把班次/待办写入手机系统日历（受系统保护，应用被清理或锁屏后仍能提醒），支持选择目标日历、立即同步、一键删除本工具写入的全部日程。
- **备份与恢复**：本地备份到应用私有目录，可新建/恢复/删除/清空备份。
- **深色模式**：跟随系统的深色/浅色切换。
  - <img width="366" height="647" alt="屏幕截图 2026-10-14 141416" src="https://github.com/user-attachments/assets/4fa55656-5bd9-4ba4-9985-ad9333134c32" /><img width="362" height="650" alt="屏幕截图 2026-10-14 141435" src="https://github.com/user-attachments/assets/67a11d8b-0195-4f80-b980-31eb995da906" />

## 隐私与安全

- **无网络能力**：`AndroidManifest.xml` 未声明 `INTERNET` 权限，系统层面禁止联网。
- **数据本地化**：排班、标签、备份均保存在应用私有目录（Room 数据库 + 本地文件）。
- **关闭系统备份**：`android:allowBackup="false"`，并通过 `res/xml/data_extraction_rules.xml` 把云备份与换机迁移一并排除，应用数据不会被系统备份服务带离本机。
- **日历权限**：仅用于把班次/待办写入你选择的系统日历；若不开启提醒，可以不授予该权限。
  注意：写入的日历若绑定了云端账户（如 Google/厂商账号），日历应用本身可能把它同步到云端，这部分由系统日历与账户设置决定，与本应用无关。
- **崩溃日志**：仅在 debug 包中记录，写入应用私有目录并只用于本机显示，release 包不启用。
- **不上传、不统计、无第三方 SDK**：除 AndroidX / Compose / Room / Kotlin 协程外无其他依赖。

## 技术栈

| 项目 | 版本 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose (Material 3) |
| 存储 | Room |
| 构建 | Gradle + Android Gradle Plugin |
| minSdk / targetSdk | 26 / 34 |

### 关于 compileSdk

本仓库当前使用 `compileSdk = 37`，并配合 `gradle.properties` 中的：

```properties
android.suppressUnsupportedCompileSdk=37
android.overridePathCheck=true
```

如果你的 Android SDK 中没有 Platform 37：

- 把 `app/build.gradle.kts` 的 `compileSdk` 改为你本地已有的版本（例如 35）；
- 相应移除或调整 `suppressUnsupportedCompileSdk`；
- `overridePathCheck` 仅用于中文路径（路径含非 ASCII 字符）时的构建，普通英文路径下可删除。

## 目录结构

```
ShiftPlanner/
├── app/
│   ├── src/main/
│   │   ├── java/com/shiftplanner/app/
│   │   │   ├── data/        # Room 实体、DAO、数据库、ViewModel、农历
│   │   │   ├── reminder/    # 系统日历同步
│   │   │   └── ui/          # Compose 页面与组件
│   │   ├── res/             # 图标、主题、字符串
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
