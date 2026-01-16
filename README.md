# BambuColor

用于拓竹耗材标签重复利用的 Android 应用。读取 NFC 标签 UID，绑定耗材信息并展示。

## 功能
- 读取 NFC 标签 UID
- 标签与耗材信息绑定
- 耗材库增删改查
- 多色耗材渐变预览
- 从 JSON 导入耗材

## 运行要求
- Android 10+（minSdk 29）未经严格测试
- 设备需开启 NFC

## 数据位置
- JSON 导入文件：`Android/data/<包名>/files/filaments_color_codes.json`
- 数据库文件：`Android/data/<包名>/files/bambu_color.db`

## 导入流程
1) 将 `filaments_color_codes.json` 放入 `Android/data/<包名>/files/`。
2) 打开配置页，点击“开始导入”。

应用内置一份 assets 默认 JSON，首次启动会自动释放到外部目录。

## 构建运行
使用 Android Studio 打开项目并运行 `app`。
