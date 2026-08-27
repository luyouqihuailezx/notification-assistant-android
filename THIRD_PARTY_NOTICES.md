# 第三方组件与发布前检查

此文件记录直接依赖及已知运行时组件。每次升级 Gradle 依赖后，必须运行 `./gradlew licenseReleaseReport` 并复核生成的 APK 资产和本文件。

## 发布阻断：ObjectBox

当前构建使用 `io.objectbox:objectbox-android:5.0.1`。其 Java/Kotlin 绑定包含 Apache-2.0 组件，但 Android 原生运行库按 **ObjectBox Binary Licence** 提供。该许可包含非转让、可撤销、禁止逆向工程及其他专有条款，不能被当作 GPL-3.0-only 组件重新授权。

因此，在以下任一项完成前，禁止把当前 APK 宣传或发布为“纯 GPL-3.0-only”软件，也不应投递到要求所有运行时组件均为自由软件的分发渠道：

1. 以 GPL 兼容的自由软件数据库替换 ObjectBox，并完成迁移与回归测试；或
2. 从 ObjectBox Ltd. 获得明确覆盖当前版本、分发方式和 GPL 组合的书面兼容授权。

该限制不改变上游 GPL 代码的许可；它说明当前二进制组合存在额外组件许可约束。

## 已核验的直接组件

| 组件 | 版本 | 许可证/说明 |
| --- | --- | --- |
| 上游 `Alfio010/notification-listener-android` | 本仓库衍生版本 | GPL-3.0；见 `LICENSE` 与 `NOTICE` |
| ObjectBox Android | 5.0.1 | ObjectBox Binary Licence；包含 Apache-2.0 与 OpenLDAP Public License 2.8 材料；见上述阻断项 |
| Zip4j | 2.11.5 | Apache License 2.0 |
| Kotlinx Datetime | 0.7.1 | Apache License 2.0；发布时复核其随附第三方通知 |
| Apache Commons Lang | 3.20.0 | Apache License 2.0 |
| Accompanist DrawablePainter | 0.37.3 | Apache License 2.0 |
| AndroidX、Jetpack Compose、Material Components、Kotlin、desugar | 见 Gradle 锁定版本 | 主要为 Apache License 2.0；以生成报告及各组件 NOTICE 为准 |
| AndroidX Security Crypto | 1.1.0-alpha06 | Apache License 2.0 |
| Material Icons 资产 | 随 SDK | Apache License 2.0；应用内保留 `material_icon_license.html` |

`junit:junit` 与 `org.json:json` 仅用于测试，不进入发布 APK。`com.jaredsburrows:gradle-license-plugin` 仅作为构建插件，不应作为运行时 `implementation` 依赖。

## 已移除的历史声明

此前 README 与旧许可证页面提到了 MPAndroidChart，但当前 Gradle 依赖与源代码均无该组件的使用证据，故不将其列为当前项目依赖。若未来重新加入，必须以 Apache-2.0 许可证和适用 NOTICE 重新纳入本清单与 APK 许可证页面。

## 维护要求

- 不得删除 `LICENSE`、`NOTICE` 或适用的第三方许可证文本。
- 依赖升级、资产增加或发布渠道变化时，重新审查许可证、NOTICE 和对应源码义务。
- 发行 APK 时，将生成的第三方许可证页面保留在 `app/src/main/assets/open_source_licenses.html`，并确保“关于”页可访问。
