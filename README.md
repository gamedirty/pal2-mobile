# Pal2Mobile · 仙剑奇侠传2 安卓移植版

把 2003 年大宇资讯《仙剑奇侠传二》（Windows 繁体版）带到安卓手机的独立 App。
移植哲学参考 [d2r-mobile](https://github.com/gamedirty/d2r-mobile)：**原版游戏程序与数据一字节不改，
由宿主层（Box64 + Wine + Turnip/Zink + cnc-ddraw + ALSA，基于 Winlator LGPL-2.1 定制）
提供全部平台服务，为触屏重新设计操作**，打成一个安装即玩的 APK。

## 构建

游戏数据（约 1.1GB）与字体**不在仓库内**，存放在 Releases 的 `game-data` 资产里，CI 自动拉取：

1. 手动本地构建：从 `game-data` Release 下载 `pal2-game.tzst` 和 `pal2-fonts.zip`，
   解压放入 `app/src/main/assets/`，然后 `./gradlew assembleDebug`。
2. GitHub Actions：推送 `v*` 标签，或在 Actions 页面手动触发 Build APK——产物自动挂到对应 Release。

依赖：JDK 17、NDK 24.0.8215888、CMake 3.22.1。

## 特性

- 一体化 APK：Wine/Box64 运行时 + 全部游戏数据内置，离线安装即玩
- 3 倍整数缩放（1920×1440）+ Lanczos 锐化着色器
- Big5 代码页 + 新細明體字体管线，对话文字完整无裁切
- 王者荣耀风格浮动摇杆 + Esc/确认/空格/快进/翻页 虚拟按键
- 点哪打哪触屏鼠标、四指呼出游戏内菜单

## 游戏数据资产的制作

```sh
scripts/pack_game.py    # 从已安装的游戏目录打包 pal2-game.tzst（含 Game_Data/Data/Fight/Idata）
scripts/make_mingliu.py # 用 fonttools 把 SimSun 改名成「新細明體」紧凑字体
scripts/gen-locale.sh   # WSL 里生成 zh_TW.UTF-8 locale
```

## 致谢与许可

- [Winlator](https://github.com/brunodev85/winlator)（LGPL-2.1）——本工程为其修改版，遵循 LGPL-2.1
- Wine · Box64（ptitSeb）· Mesa/Turnip/Zink · cnc-ddraw · Termux Pacman glibc 补丁
- 《仙剑奇侠传二》© 大宇资讯——本仓库不含任何游戏内容，游戏数据仅供自备副本的个人使用
