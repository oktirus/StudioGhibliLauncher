# Оновлення Долини Ghibli

Установлена нативна версія перевіряє стабільні випуски [oktirus/StudioGhibliLauncher](https://github.com/oktirus/StudioGhibliLauncher/releases/latest). У **Налаштування → Оновлення Ghibli** можна перевірити їх вручну. Після відкриття лаунчер перевіряє випуск, якщо від останньої успішної перевірки минула доба. Якщо є новіша версія, біля годинника з’являється позначка **Нова версія**. Вона зберігається й після повторного запуску. Діалог із пропозицією з’являється, коли головний екран вільний.

За наявності більшого `versionCode` лаунчер пропонує завантаження, перевіряє розмір, `SHA-256`, `applicationId` (`ua.ghibli.tv.valley`) та сертифікат підпису, після чого показує кнопку **Встановити**. Першого разу Android може попросити дозвіл встановлювати програми з **Ghibli · Вітер над долиною**. Після повернення потрібно ще раз підтвердити встановлення. Остаточне рішення приймається в системному вікні Android. Відмова залишає попередню версію і всі дані; `HOME` updater не змінює.

Ключ підпису не передається до GitHub. CI-артефакти GitHub Actions підписані окремим тимчасовим debug-ключем runner і **не є каналом сумісних оновлень**; для встановлення й оновлення бери APK зі сторінки [GitHub Releases](https://github.com/oktirus/StudioGhibliLauncher/releases/latest).

## Публікація наступного випуску

1. Збільшити `versionCode` і `versionName` у `app/build.gradle`; не використовувати той самий `versionCode` для іншого APK.
2. Зібрати локально тим самим ключем: `pwsh -NoProfile -File Build.ps1`. Пройти всі тести/lint і переглянути змінений UI.
3. Підготувати UTF-8 release notes у `docs/releases/<version>.md`. Згенерувати й перевірити staging без публікації:

```powershell
python -X utf8 tools/publish_release.py --apk releases/GhibliValley-1.0.0-debug.apk --notes-file docs/releases/1.0.0.md
```

4. Після коміту, пушу та зеленого GitHub Android workflow (`android.yml`) додати `--publish`. Скрипт звіряє сертифікат із `.release-certificate.sha256`, створює draft із `GhibliValley-<version>.apk` та `update.json`, завантажує їх назад для readback-перевірки й лише потім публікує стабільний реліз. Наявний tag не перезаписується.

Формат `update.json`: `schemaVersion=1`, `applicationId`, integer `versionCode`, `versionName`, `apkName`, `bytes`, `sha256`, `notes`. APK має бути asset того самого релізу. У APK немає GitHub token. Власний keystore зберігай окремо й резервуй безпечно; зміна ключа порушує сумісність оновлень.

Джерела: [GitHub Releases API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release), [PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller).
