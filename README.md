# Offline WebView

Минимальное Android-приложение, которое открывает принадлежащий вам сайт в
`WebView`, сохраняет успешные GET-ответы с его хоста и использует последнюю
сохранённую копию при сетевой ошибке.

## Быстрый старт

Адрес сайта задаётся Gradle-свойством:

```properties
START_URL=https://gmentor.ru/
```

Его можно поместить в локальный `gradle.properties` (не коммитить секреты) или
передать сборке:

```text
./gradlew assembleDebug -PSTART_URL=https://gmentor.ru/
```

Готовый debug APK появится в:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Текущие границы

- перехватываются только GET-запросы `http`/`https` на хост `START_URL`;
- ответы с `Cache-Control: no-store` не сохраняются;
- при успешной сети используется свежий ответ, при сетевой ошибке — кэш;
- cookies WebView передаются серверу и принимаются обратно;
- POST, загрузки файлов, `blob:`, WebSocket и сторонние хосты идут через WebView;
- кэш ограничен 256 МБ, самые старые записи удаляются первыми.

Подробный план и решения находятся в [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
