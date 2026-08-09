# Regression Framework — промежуточный архитектурный handoff

## 1. Назначение документа

Документ фиксирует состояние мультимодульного Java-фреймворка после завершения рефакторинга `regression-petstore-api` и учитывает ранее сформированную архитектуру `regression-jhipster`.

Он предназначен для разработчиков и AI-агентов, которые будут:

- добавлять новые продуктовые подпроекты;
- расширять `regression-core`;
- рефакторить Petstore или JHipster;
- унифицировать сборку, отчётность и CI.

Перед изменением корневой сборки необходимо дополнительно проверить активный parent `pom.xml`, поскольку состав модулей будет расширяться.

## 2. Текущее состояние экосистемы

В проанализированном parent POM зарегистрированы:

```text
regression
├── regression-core
├── regression-petstore-api
└── regression-jhipster
```

Роли модулей:

| Модуль | Роль |
| --- | --- |
| `regression-core` | Общая конфигурация, request model/builder, Jersey transport, JSON, response validation, конвертеры и совместимая Cucumber-инфраструктура |
| `regression-petstore-api` | Независимый API test module на чистом JUnit 5 |
| `regression-jhipster` | Hybrid API/UI module на Cucumber, PicoContainer, Jersey и Playwright |

Главный результат: общий фреймворк поддерживает несколько test-runner моделей. Унифицировать нужно транспорт, конфигурацию и архитектурные правила, а не принудительно верхний тестовый DSL.

## 3. Базовая архитектурная модель

### Общая нижняя часть

```text
Product module
    → product-specific base API service
    → GeneralApiService
    → shared Request model
    → Jersey Client / Jackson
    → target API
```

`regression-core` должен оставаться независимым от конкретного продукта. В него допустимо переносить только доказанно повторяемые механизмы.

В core не должны попадать:

- Petstore/JHipster endpoints;
- product DTOs;
- product-specific Steps;
- локаторы и Page Objects;
- бизнес-cleanup конкретной сущности;
- Allure только ради одного модуля.

### Допустимые верхние модели

Pure JUnit API:

```text
JUnit Test
    → Domain Steps
    → Endpoint Service
    → shared API infrastructure
```

Cucumber API:

```text
Feature
    → Definition
    → Domain Steps
    → Endpoint Service
    → shared API infrastructure
```

Cucumber UI:

```text
Feature
    → Definition
    → UI Steps
    → Page Object / Component
    → Playwright
```

Выбор Cucumber должен обосновываться читаемыми бизнес-спецификациями или существующим командным стандартом. Для технических API-проверок без нетехнических читателей JUnit является предпочтительной упрощённой моделью.

## 4. Итоговая оценка regression-petstore-api

### Фактический состав

Финальный архив содержит:

- 6 OpenAPI-generated models;
- 5 handwritten production API classes;
- 6 test/test-support classes;
- 5 JUnit test methods;
- Maven, JUnit Platform и Allure configuration;
- `run-tests.sh`;
- самостоятельный README.

Основные слои:

```text
src/main/java
├── models/generated
├── services
└── steps

src/test/java
├── data
├── extensions
└── tests
```

Это правильное разделение: переиспользуемый API client layer находится в `main`, а тестовые фабрики, lifecycle и assertions — в `test`.

### Сильные стороны

1. Cucumber/Gherkin полностью удалены из модуля, но совместимость с экосистемой сохранена.
2. OpenAPI DTO используются непосредственно для сериализации, десериализации и assertions.
3. Services владеют HTTP mechanics: endpoint, method, body, expected status и response type.
4. Steps сохраняют product-oriented orchestration и логируют только подтверждённые успешные операции.
5. Assertions находятся в JUnit tests, а не в Services.
6. DataFaker скрыт за фабриками; тесты не содержат длинных конструкторов и фиксированных общих данных.
7. `TestRunId` использует run-specific значения и атомарные последовательности.
8. `CleanupExtension` регистрирует cleanup сразу после создания, выполняет его в LIFO-порядке, продолжает после отдельной ошибки и агрегирует failures.
9. Параллельность включена явно и ограничена фиксированным пулом из четырёх потоков.
10. Store Order shared state защищён `ResourceLock`.
11. Allure содержит hierarchy metadata, severity, tags, failure categories и работающий Trend.
12. `run-tests.sh` генерирует отчёт даже при failed tests и возвращает исходный test exit code.

### Архитектурный компромисс Steps

Для текущего маленького API часть Steps является тонкой делегацией. В изолированном JUnit-проекте тесты могли бы обращаться к Services напрямую.

Steps всё же оправданы как:

- единая product-facing граница;
- место будущей orchestration нескольких calls;
- место domain logging;
- архитектурное соответствие другим модулям.

Правило для продолжения: не удалять Steps механически, но и не добавлять pass-through методы без использования или ожидаемой orchestration.

## 5. Test data, cleanup и parallel safety

### Pet data

Pet IDs, Category IDs, Tag IDs и строковые значения уникализируются на уровне run/JVM. Это подходит для параллельных проверок в одном процессе.

### Store Order data

Order IDs циклически ограничены диапазоном `1..10`, поэтому Store Order suite сериализован через общий lock `petstore-orders`.

Ограничения:

- lock действует только внутри одной JUnit Platform/JVM;
- параллельные CI jobs или Maven forks не видят lock друг друга;
- будущие Store Order test classes обязаны использовать тот же lock;
- после десяти активных/грязных ID последовательность повторяется.

### Cleanup

Текущий extension корректно покрывает create/get scenarios. Однако delete test создаёт Order и затем удаляет его как проверяемое действие без fallback registration. Если delete operation падает до успешного удаления, сущность может остаться.

Рекомендуемое развитие cleanup abstraction:

- поддержка unregister/cancel после успешного tested delete;
- либо idempotent cleanup, допускающий `404`;
- сохранение исходного assertion/API failure как primary failure;
- отдельная стратегия аварийной очистки для CI.

Ни JUnit extension, ни Cucumber hook не выполнятся после `kill`, OOM, agent termination или hard timeout. Для контролируемых сред требуется namespace/run-id cleanup job или TTL на тестовых данных.

## 6. Reporting и execution workflow

Petstore использует module-local Allure dependencies и plugin configuration. Это соответствует решению не добавлять Allure в `regression-core` ради одного подпроекта.

Реализовано:

- `allure-jupiter`;
- `categories.json` для cleanup, HTTP 5xx, network/timeout и JSON mapping failures;
- `executor.json` и `environment.properties` из скрипта;
- ручной перенос Allure 2 history через `local-allure-history`;
- sequential build order;
- локальный report server через `jwebserver`.

`historyEnabled=false` в Maven plugin является осознанным: существует один источник истории — явный script workflow.

Ограничения:

- Trend локален и не переживёт ephemeral CI agent без cache/artifact;
- скрипт ориентирован на interactive local run и блокируется до остановки web server;
- для CI нужен режим без открытия браузера и без ожидания сервера;
- при нескольких Allure-enabled modules имеет смысл поднять только version/plugin management в parent POM, но не переносить product reporting logic в core.

## 7. Build orchestration

Команда:

```text
mvn -pl :regression-petstore-api -am test
```

запускает фазу `test` также для `regression-core`, включая его Cucumber feature tests. Это нормальное поведение Maven reactor, но нежелательное для быстрого Petstore-only run.

Текущий скрипт использует двухэтапную стратегию:

```text
install regression-core with maven.test.skip=true
    → test regression-petstore-api without -am
```

Такой workaround корректен, но показывает необходимость будущей общей стратегии запуска модулей. Возможные направления:

- root Maven profiles для module-only execution;
- разделение core unit tests и Cucumber demonstration/acceptance tests;
- единый CI pipeline с независимыми jobs на модули;
- общий script convention с опциями `--no-open`, tag/test filters и CI mode.

## 8. OpenAPI governance

Текущая политика:

- генерируются только models;
- generated classes не редактируются вручную;
- endpoint clients пишутся в архитектуре framework Services;
- generation по умолчанию может быть отключена property;
- регенерация выполняется осознанно после contract change.

Глобальное обновление OpenAPI Generator опасно. Ранее изменения generator configuration ломали generated sources других продуктов. Root snapshot использует централизованную версию, а JHipster в ходе развития требовал module-specific adjustments.

Правило для агентов:

1. не менять generator version глобально ради одного модуля;
2. сначала проверить Petstore и JHipster generation/compile;
3. при необходимости использовать module-scoped override;
4. проверить, что generated models не ссылаются на отсутствующие `ApiClient` или supporting files;
5. фиксировать source spec, generator version и config options в README/PR.

Jackson DTO compatibility не является полной OpenAPI validation. Schema/status/media-type validation должна добавляться отдельным contract layer, а не ослаблением десериализации.

## 9. Контекст regression-jhipster

`regression-jhipster` остаётся hybrid Cucumber API/UI модулем для локального JHipster application.

### API

```text
Feature
    → Definition
    → Steps
    → Domain Service
    → ApiService / GeneralApiService
    → Jersey
```

Ключевые решения:

- Definitions тонкие: binding, DataTable conversion и VariablesController;
- Definitions не извлекают AuthService/Service из Steps;
- Auth headers инкапсулированы в Steps/AuthService;
- AuthService должен оставаться scenario-scoped через PicoContainer;
- Steps сохранены как records;
- domain logging находится на Steps level;
- generated models остаются strict;
- contract mismatch исправляется в tested application, а не выключением Jackson strictness;
- cleanup по имени удаляет все точные совпадения, а не только `findFirst()`.

### UI

```text
Feature
    → Definition
    → Steps
    → Page Object
    → Component
    → Playwright
```

Ключевые решения:

- Definitions не содержат locators, private helper logic и browser calls;
- Steps владеют scenario workflow;
- Pages владеют page behavior и readiness;
- Components моделируют подтверждённые reusable DOM regions;
- UI beans не знают о Playwright;
- Hooks владеют browser lifecycle и failure artifacts;
- Page/BrowserContext не должны быть static или shared между потоками;
- добавляются только страницы и методы, подтверждённые реальным сценарием и DOM;
- API используется для быстрой подготовки/очистки данных hybrid scenarios.

Текущий browser-per-scenario lifecycle медленнее, но прост и изолирован. Переход к browser-per-thread/process допустим только с новым isolated BrowserContext на scenario и явным thread ownership.

### Незавершённые приоритеты JHipster

- atomic initialization и lifecycle hardening Playwright resources;
- failure-safe screenshot/trace cleanup;
- dedicated UI assertion timeout;
- ID-based post-scenario cleanup registry;
- pagination-aware API cleanup;
- browser reuse только после подтверждения thread safety;
- contract validation как отдельный слой;
- перенос generic Playwright infrastructure в core только после появления второго реального UI consumer.

Definitions в `src/main/java` остаются legacy convention JHipster/framework. Новые pure test modules должны использовать стандартный `src/test/java`. Массовый перенос glue следует делать только как отдельную согласованную миграцию.

## 10. Petstore и JHipster: что унифицировать, а что нет

| Область | Petstore | JHipster | Решение |
| --- | --- | --- | --- |
| Test runner | JUnit 5 | Cucumber/JUnit Platform | Не унифицировать принудительно |
| API transport | core Jersey | core Jersey | Общий core |
| Models | OpenAPI-generated | OpenAPI-generated | Общая policy, product packages |
| Steps | JUnit domain facade | Cucumber orchestration | Сохранять product level |
| Assertions | JUnit/AssertJ tests | Then flow/Page domain assertions | Контекстно-зависимо |
| Test data | DataFaker factories | DataTable + Populator/Variables | Не смешивать без необходимости |
| Cleanup | JUnit Extension | API pre-cleanup, future Cucumber registry | Общий принцип, разные lifecycle adapters |
| Parallelism | JUnit pool + lock | Scenario scope, Playwright isolation | Общие safety rules |
| Reporting | Allure JUnit + local Trend | Cucumber screenshots/traces | Унифицировать только при общей потребности |
| UI | отсутствует | Playwright Pages/Components | Product-specific |

## 11. Общие правила для новых модулей

### Выбор runner

Использовать JUnit 5, если:

- это технические API/contract checks;
- Gherkin не читается PO/BA;
- DataTable/Definitions не добавляют ценности.

Использовать Cucumber, если:

- сценарии являются executable business specification;
- нужен hybrid API/UI narrative;
- команда реально использует Gherkin как коммуникационный слой.

### Обязательные границы

- HTTP mechanics — Services.
- Business orchestration — Steps.
- Assertions — test/Then/page-domain level, но не Services.
- Generated DTO — untouched.
- Test data — test layer.
- Locators — Pages/Components.
- Lifecycle — JUnit Extension или Cucumber Hooks.
- Configuration — properties/core, не Page Objects и не tests.
- Product code не переносится в core ради предполагаемого reuse.

### Parallel readiness

Перед включением concurrent mode агент обязан определить:

- ownership каждого mutable object;
- уникальность данных;
- shared external resources;
- границы JVM/process/CI lock;
- cleanup при partial failure;
- thread safety клиента и UI driver objects.

### Минимальный deliverable нового модуля

- module POM, наследующий parent;
- module-specific configuration;
- generated model policy;
- Services и Steps;
- test-data strategy;
- cleanup strategy;
- positive и negative representative tests;
- parallel execution decision;
- reporting configuration;
- README с commands, limitations и extension rules.

## 12. Найденные проблемы финального petsrc.zip

### P1 — line endings run-tests.sh

`run-tests.sh` в архиве сохранён с CRLF. На Linux статическая проверка завершается syntax error возле `{\r`.

Необходимое исправление:

```gitattributes
*.sh text eol=lf
```

После добавления правила файл нужно один раз нормализовать в LF. Это важно для Linux CI и прямого запуска через shebang.

### P2 — archive/README mismatch

README показывает:

```text
local-allure-history/.gitignore
```

но в `petsrc.zip` этот файл отсутствует. `run-tests.sh` создаёт directory сам, поэтому runtime не сломан, однако source package не полностью совпадает с документацией. Нужно проверить фактический Git repository и убедиться, что placeholder отслеживается.

### P2 — delete cleanup fallback

Delete Order test не имеет fallback cleanup, если tested delete не выполнился. Это не блокирует текущий scope, но должно быть учтено до расширения CRUD coverage.

### P2 — local-only locks and history

`ResourceLock` и `local-allure-history` не решают cross-process/CI coordination. Для нескольких jobs нужны external isolation и persisted artifacts.

## 13. Проверенный статус

Подтверждено пользователем:

- root reactor `clean compile` завершался успешно для core, Petstore и JHipster;
- Petstore JUnit suite запускается;
- Allure report генерируется;
- failure categories заполняются;
- Trend сохраняет успешные и неуспешные запуски;
- module script больше не запускает core Cucumber tests после разделения build/test commands.

Проверено по финальному архиву:

- структура соответствует README и принятой архитектуре, кроме отсутствующего history placeholder;
- `pom.xml` является валидным XML;
- `categories.json` является валидным JSON;
- обнаружены CRLF line endings в Bash script.

Полный Maven run в среде текущей инспекции не выполнялся, поскольку Maven в ней отсутствует. Runtime conclusions основаны на предоставленных пользователем успешных запусках и статической инспекции финального source archive.

## 14. Приоритеты следующих этапов

### Общий framework

1. Добавить root `.gitattributes` и закрепить line-ending policy.
2. Разработать единый module execution convention для local/CI без запуска unrelated tests.
3. Определить CI policy для Allure history и reports.
4. Зафиксировать OpenAPI Generator compatibility matrix по модулям.
5. Переносить utilities в core только после повторного использования минимум в двух модулях.

### Petstore

1. Добавить CI/non-interactive mode в `run-tests.sh`.
2. Усилить delete cleanup fallback.
3. Добавить API request/response attachments в Allure с sanitization.
4. Добавить controlled retry только для подтверждённых transient transport failures.
5. Рассмотреть local Petstore deployment для deterministic CI.
6. Добавить schema/contract validation отдельным слоем.

### JHipster

1. Завершить lifecycle hardening Playwright.
2. Реализовать ID-based scenario cleanup registry.
3. Настроить assertion timeout независимо от BrowserContext timeout.
4. Решить pagination для API cleanup.
5. Добавлять UI pages/components только после scenario demand и DOM inspection.
6. Не оптимизировать browser reuse до подтверждения thread ownership.

## 15. Финальный вывод

На текущем этапе структура мультимодульного framework логична и масштабируема.

`regression-core` формирует общую техническую платформу. `regression-petstore-api` показывает компактный и понятный JUnit-подход для API automation. `regression-jhipster` показывает более богатую Cucumber hybrid architecture, где BDD оправдано business flow и объединением API/UI.

Главная архитектурная ценность проекта — не одинаковое количество слоёв во всех модулях, а одинаково строгие границы ответственности. Следующие агенты должны сохранять эти границы, выбирать runner осознанно, не переносить product code в core преждевременно и рассматривать parallelism, cleanup, reporting и contract generation как обязательные части дизайна нового модуля.
