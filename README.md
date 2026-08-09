# 馃幍 Sekai Song Player 路 浜屾鍏冮煶涔愭挱鏀惧櫒

> **LRC 姝岃瘝鍚屾 路 缃戞槗浜戝垎浜摼鎺ョ洿鎾?路 鎺堟潈浣撶郴**
> Anime music player with LRC lyrics sync, NetEase share-link playback & licensing system

[![Java](https://img.shields.io/badge/Java-17-orange)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![MyBatis](https://img.shields.io/badge/MyBatis-3-lightgrey)](https://mybatis.org/)
[![Thymeleaf](https://img.shields.io/badge/Thymeleaf-3-005F0F)](https://www.thymeleaf.org/)
[![LRC](https://img.shields.io/badge/LRC-姝岃瘝鍚屾-brightgreen)](#)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

浜屾鍏冧富棰橀煶涔愭挱鏀惧櫒锛氫笓灞炲姩婕瓕鏇插簱銆丩RC 姝岃瘝鍚屾銆佽嚜瀹氫箟姝屽崟涓庢巿鏉冧綋绯伙紝鍐呯疆鍟嗕笟鍖栨湇鍔￠〉闈€?
An anime-themed music player with your own song library, synchronized LRC lyrics, playlists, and a licensing system.

## 鉁?鍔熻兘浜偣 Features

- 鐢ㄦ埛娉ㄥ唽銆佺櫥褰曘€侀€€鍑?User auth
- 涓撳睘鍔ㄦ极姝屾洸搴?Anime song library
- 鑳屾櫙浜屾鍏冩挱鏀惧櫒鐨偆 Anime-themed skins
- **LRC 姝岃瘝鍚屾鏄剧ず** LRC lyric sync
- 鏀惰棌姝屾洸 Favorites
- 鑷畾涔夋瓕鍗曞拰姝屽崟姝屾洸绠＄悊 Custom playlists
- 姝屾洸/姝屽崟鎼滅储 Search
- **缃戞槗浜戝崟鏇插垎浜摼鎺ョ洿鎺ユ挱鏀?* NetEase share-link playback锛堢矘璐村垎浜摼鎺ュ嵆鍙級
- **鍟嗕笟鍖栨湇鍔￠〉**锛歚/business`锛堟帴鍗曚粙缁嶏級銆乣/service`锛堝叕寮€鑾峰锛夈€乣/license`锛堟巿鏉冧腑蹇冿級銆乣/business/licenses`锛堟巿鏉冪爜绠＄悊锛夈€乣/business/leads`锛堢嚎绱㈠悗鍙帮級

## 馃挵 鍟嗕笟鍖栧畾浣?Monetization

鎺ㄨ崘鎶婇」鐩綔涓?绉佹湁鍖栭煶涔愬簱鎼缓鏈嶅姟"鍞崠锛?
- 鉁?鍙互鏀惰垂锛氭湰鍦伴儴缃层€丯AS/鏈嶅姟鍣ㄩ儴缃层€佷富棰樺畾鍒躲€佹暟鎹縼绉汇€佸浠芥仮澶嶃€侀暱鏈熺淮鎶?- 鈿狅笍 涓嶈鍞崠锛歈Q 闊充箰銆佺綉鏄撲簯闊充箰銆丅 绔欑瓑绗笁鏂瑰钩鍙扮殑姝屾洸/瑙嗛璧勬簮鎴栧叕寮€鍦ㄧ嚎鎾斁鏇插簱
- 馃搶 鐢ㄦ埛涓婁紶鍐呭搴旂敱鐢ㄦ埛鑷纭鎷ユ湁鍚堟硶浣跨敤鏉?
榛樿娴嬭瘯璐﹀彿锛歚sekai` / `123456`

## 馃搧 澶栭儴涓婁紶鐩綍 External Uploads

澶т綋绉湰鍦伴煶棰戝彲鏀惧閮ㄧ洰褰曪細

```bash
$env:SEKAI_EXTERNAL_UPLOAD_ROOT="D:/YourUploadRoot/uploads"
mvn spring-boot:run
```

URL 鍓嶇紑 `/external-uploads/**`銆?
## 馃梽锔?鏁版嵁搴?Database

琛ㄥ垱寤哄湪 `sekai_friend` 鏁版嵁搴撲腑锛屽墠缂€ `sekai_song_`锛?
```sql
SOURCE build-data/sekai-song.sql;
```

## 鈻讹笍 鍚姩 Run

```bash
mvn spring-boot:run
```

榛樿绔彛锛歚8085`

骞冲彴鍙戝竷鏂囨瑙?`docs/monetization-platform-copy.md`銆?
## 馃搫 License

[MIT](LICENSE) 漏 2026 [sekai-lyr](https://github.com/sekai-lyr)


<p align="center">
  <img src="screenshots/demo.webp" alt="Demo" width="720"/>
</p>
---

**猸?If this project helped you, star it! 濡傛灉杩欎釜椤圭洰瀵逛綘鏈夊府鍔╋紝娆㈣繋 Star锛?*

