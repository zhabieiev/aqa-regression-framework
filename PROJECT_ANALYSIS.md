# Project Architecture Analysis - Regression Test Framework

**Analysis Date:** July 11, 2026  
**Project Type:** Maven-based Cucumber BDD Testing Framework  
**Java Version:** 21  
**Technology Stack:** 
- Cucumber (7.18.1) - BDD Framework
- Jersey REST Client (3.1.3)
- Jackson (2.15.1) - JSON/XML processing
- AWS SDK (2.36.2) - S3 Integration
- Lombok (1.18.32) - Code generation
- AssertJ (3.26.3) - Fluent assertions

---

## 1. ARCHITECTURAL OVERVIEW

### Main Pattern: Layered Architecture
```
┌─────────────────────────────────────────────────────────────────────┐
│                    STEP DEFINITIONS (Cucumber)                       │
│  (ExampleDefinitions, GeneralDefinitions, S3Definitions, etc)      │
└────────────────────────┬──────────────────────────────────────────┘
                         │ Uses
┌────────────────────────▼──────────────────────────────────────────┐
│                    SERVICES LAYER                                  │
│  • GeneralApiService (REST API Client)                            │
│  • S3ServiceActions (AWS S3 Operations)                           │
│  • S3ClientProvider (S3 Connection Management)                    │
└────────────────────────┬──────────────────────────────────────────┘
                         │ Uses
┌────────────────────────▼──────────────────────────────────────────┐
│                    CONTROLLERS LAYER                               │
│  • PropertiesController (Configuration)                           │
│  • ClientController (HTTP Client Creation)                        │
│  • ClassController (Dynamic Class Loading)                        │
│  • VariablesController (Test Variable Storage)                    │
└────────────────────────┬──────────────────────────────────────────┘
                         │ Uses
┌────────────────────────▼──────────────────────────────────────────┐
│                    MODELS LAYER                                    │
│  • Request, Example, ImageMetadata, S3FileMetaData               │
└────────────────────────┬──────────────────────────────────────────┘
                         │ Uses
┌────────────────────────▼──────────────────────────────────────────┐
│          UTILITIES & CONVERTORS (Cross-cutting concerns)          │
│  • FileParseUtils, FileUtils, WaitUtils                          │
│  • JsonConvertor, DateConverter, StringConvertor, etc.           │
│  • Enumerations & Configuration                                   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. DETAILED COMPONENT DESCRIPTION

### 2.1. CONTROLLERS (Infrastructure Layer)

#### PropertiesController (Configuration Management)
- **Purpose:** Centralized access to application properties
- **Type:** Singleton Pattern
- **Key Properties:**
  - AWS configuration: `AWS_REGION`, `AWS_ENDPOINT`, `AWS_ROLE_ARN`
  - Timing settings: `WAIT_CONDITION_TIMEOUT`, `RETRY_TIMEOUT`, `EXPLICIT_WAIT`
  - Credentials: `USER_ADMIN`, `USER_ADMIN_PASSWORD`
  - Models: `MODEL_PACKAGE`
- **Capabilities:**
  - Support for nested properties
  - Override via system variables
  - Support for different environments (dev, qa, prod)

#### ClientController (HTTP Client Creation)
- **Purpose:** Factory for creating JAX-RS REST clients
- **Dependencies:** FileParseUtils, ObjectMapper
- **Key Function:** Creating WebTarget with configured JSON providers

#### ClassController (Dynamic Class Loading)
- **Purpose:** Loading models by class name from configuration
- **Usage:** For dynamic serialization/deserialization of test data
- **Dependency:** Property enum

#### VariablesController (Variable Storage)
- **Purpose:** Storing test variables during execution
- **Type:** Instance-based storage (not singleton)
- **Feature:** Support for nested property access via reflection

---

### 2.2. SERVICES (Business Logic)


#### GeneralApiService (Abstract REST Client)
```
┌─ GeneralApiService (Abstract)
│  ├─ createRequest(method, path) → Request.Builder
│  ├─ addParams(WebTarget, params) → WebTarget with query parameters
│  ├─ addHeaders(Invocation.Builder, headers) → Builder with headers
│  ├─ sendRequest(builder, method) → Response with validation
│  └─ Support DateConverter for timestamp transformation
```
- **Dependencies:**
  - ClientController (for client creation)
  - DateConverter (for date transformation)
  - Request model (for request building)
- **Functions:**
  - Building REST requests
  - Managing parameters and headers
  - HTTP status validation

#### S3ClientProvider (S3 Client Factory)
- **Type:** Singleton
- **Supported Providers:**
  - SSO (Single Sign-On)
  - REMOTE (Remote credentials)
- **Capabilities:**
  - S3Client caching
  - Dynamic provider switching

#### S3ServiceActions (S3 Operations)
- **Operations:**
  1. **delete(bucket, key)** - delete objects from S3
  2. **upload(bucket, key, data)** - upload data with GZIP compression
  3. **getObject(bucket, key)** - get objects with format support
- **S3 Content Formats:**
  - JSON_ARRAY
  - COMMA_SEPARATED_JSONS
- **Modeling:** Depends on S3FileMetaData

---

### 2.3. MODELS (Data Models)

#### Request Model
```java
Request {
  - method: String (GET, POST, PUT, DELETE)
  - path: String
  - params: Map<String, String>
  - headers: Map<String, String>
  - body: String
  - statusCode: Integer
  
  Builder pattern for convenient construction
}
```

#### Example Model
- **Purpose:** Test model with all data types
- **Fields:**
  - Primitives: byte, short, int, long, float, double, boolean, char
  - Objects: String, Integer, Long, Double
  - Collections: List<String>, List<Integer>, List<Object>
  - Arrays: int[], Object[]

#### ImageMetadata Model
- **Fields:**
  - width: int
  - height: int

#### S3FileMetaData Model
- **Fields:**
  - key: String
  - format: S3FileContentFormat
- **Methods:**
  - filtering by prefixes
  - content format configuration

---

### 2.4. CONVERTORS (Data Transformers)

#### 1. JsonConvertor
```
Map<String, String> ──→ JSON String
├─ Support for nested fields (dot notation: "user.name")
├─ Support for arrays (indexing)
└─ Uses Jackson ObjectMapper
```

#### 2. DateConverter
```
String (Pattern) ──→ Date
├─ Supported patterns:
│  ├─ "now" - current time
│  ├─ "now+1d" / "now-2h" - relative offsets
│  │  (d=day, m=minute, h=hour, w=week, M=month)
│  ├─ "now+1d/M" - with month rounding
│  └─ Support for timezones
└─ Converts to epoch format
```

#### 3. StringConvertor (String Templating)
```
String Template ──→ Converted String
├─ ${property.name} - substitute from configuration
├─ @{variable.name} - substitute from variables
├─ date:{pattern} - substitute date by pattern
└─ file:{path} - substitute file contents
```
**Example:** `"username: ${USER_ADMIN}, created: date:{now+1d}"`

#### 4. MapConvertor
```
Map<String, String> ──→ Map<String, String>
├─ Transformation of nested keys
├─ Filtering by prefixes (query:, body:, headers:, path:)
└─ Stream-based implementation
```

#### 5. ObjectConvertor
```
Object ──→ Map<String, String>
├─ Recursive field transformation
├─ Retention logic for comparisons
└─ Support for nested objects
```

---

### 2.5. ENUMERATIONS (Configuration)

#### Property Enum
```java
AWS_REGION, AWS_ENDPOINT, AWS_ROLE_ARN,
WAIT_CONDITION_TIMEOUT, RETRY_TIMEOUT, EXPLICIT_WAIT,
USER_ADMIN, USER_ADMIN_PASSWORD,
MODEL_PACKAGE
```

#### RequestPrefixes Enum
```java
QUERY("query:"),
PATH("path:"),
BODY("body:"),
HEADERS("headers:"),
RESPONSE("response:")
```

#### RequestParams Enum
```java
STATUS_CODE, ID
```

#### S3FileContentFormat Enum
```java
JSON_ARRAY,
COMMA_SEPARATED_JSONS
```

---

### 2.6. UTILITIES (Helpers)

#### FileParseUtils (JSON/XML Processing)
- **Type:** Singleton with lazy initialization
- **Functions:**
  - `read(String, Class<T>)` - deserialization
  - Support for JSON and XML formats
  - ObjectMapper caching for performance improvement

#### FileUtils (File Operations)
- **Functions:**
  - `readFile(Path)` - read file contents
  - `compressToGzip(byte[])` - GZIP compression
  - `createTempFile(String)` - create temporary files

#### WaitUtils (Asynchronous Waiting)
- **Variants:**
  1. `wait(BiPredicate, Object, timeout)` - wait for condition with two parameters
  2. `wait(Supplier, timeout)` - wait for supplier result
- **Capabilities:**
  - Customizable timeouts
  - Polling support

#### ImageMetadataUtils (Image Processing)
- **Functions:**
  - Load images by URL
  - Read metadata (width, height)

#### AssertionConfigurationUtils (Assertion Configuration)
- **Functions:**
  - Create RecursiveComparisonConfiguration
  - Support regex patterns for flexible comparison

---

### 2.7. STEP DEFINITIONS (Cucumber Tests)

#### StepArgumentConverters (Parameter Transformers)
```
DataTable ──→ Map<String, String>  (DataTableType)
│           └─ Applies StringConvertor to values
│
String ──→ Converted String  (ParameterType)
└─ Substitute variables and properties
```

#### GeneralDefinitions (Base Assertions)
```
├─ assertStringVariable(name, expected)
├─ assertIntVariable(name, expected)
├─ assertObjectVariableEquals(name, expected)
└─ assertListVariables(varName, expectedList)
  └─ Support regex comparison
```

#### ExampleDefinitions (Example Model Tests)
```
├─ populateExampleFromMap(dataTable)
├─ populateExampleListFromTable(dataTable)
├─ compareExampleFields(fieldName, expected)
├─ validatePrimitiveConversions(...)
└─ compareStringConversion(...)
```

#### S3Definitions (S3 Operations)
```
├─ getFilesFromS3(bucket, prefix, format)
├─ filterFilesBy(filterMap)
├─ uploadToS3(bucket, key, data)
└─ validateS3FileMetadata(...)
```

#### ImageMetadataDefinitions (Image Operations)
```
└─ readImageDimensions(imageUrl)
```

---

## 3. DEPENDENCY GRAPH BETWEEN CLASSES

```
┌─────────────────────────────────────────────────────────────┐
│              STEP DEFINITIONS                               │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────┐   ┌──────────────────────┐       │
│  │ ExampleDefinitions   │   │ GeneralDefinitions   │       │
│  │                      │   │                      │       │
│  │ Uses:                │   │ Uses:                │       │
│  │ • Populator ────┐    │   │ • VariablesController        │
│  │ • ClassController    │   │ • AssertionConfigUtils       │
│  │                │     │   │ • ObjectConvertor           │
│  └──────────────────┼───┘   └──────────────────────┘       │
│                    │                                         │
│  ┌──────────────────┼───────┐      ┌──────────────────┐    │
│  │ S3Definitions    │       │      │ ImageMetadata    │    │
│  │                  │       │      │ Definitions      │    │
│  │ Uses:            │       │      │                  │    │
│  │ • S3ServiceActions       │      │ Uses:            │    │
│  │ • StringConvertor        │      │ • ImageMetadataUtils   │
│  └────────────────────────┘       └──────────────────┘    │
│                                                              │
│  ┌──────────────────────┐                                   │
│  │ StepArgumentConverters                                  │
│  │                      │                                   │
│  │ Uses:                │                                   │
│  │ • StringConvertor    │                                   │
│  └──────────────────────┘                                   │
└────────┬──────────────────────────────────────────────────┘
         │
         │ Uses
         │
┌────────▼──────────────────────────────────────────────────┐
│              SERVICES LAYER                               │
├───────────────────────────────────────────────────────────┤
│                                                            │
│  ┌─────────────────────────┐    ┌──────────────────────┐ │
│  │ GeneralApiService       │    │ S3ServiceActions     │ │
│  │ (Abstract REST Client)  │    │                      │ │
│  │                         │    │ Uses:                │ │
│  │ Uses:                   │    │ • S3ClientProvider   │ │
│  │ • ClientController      │    │ • S3FileMetaData     │ │
│  │ • DateConverter         │    │ • FileUtils          │ │
│  │ • Request Model         │    │ • RequestPrefixes    │ │
│  │                         │    │                      │ │
│  └─────────────────────────┘    └──────────────────────┘ │
│                                                            │
│  ┌──────────────────────────────┐                         │
│  │ S3ClientProvider (Singleton) │                         │
│  │                              │                         │
│  │ Uses:                        │                         │
│  │ • Property Enum              │                         │
│  └──────────────────────────────┘                         │
└────────┬──────────────────────────────────────────────────┘
         │
         │ Uses
         │
┌────────▼──────────────────────────────────────────────────┐
│            CONTROLLERS LAYER                              │
├───────────────────────────────────────────────────────────┤
│                                                            │
│  ┌──────────────────────┐    ┌──────────────────────┐   │
│  │ PropertiesController │    │ ClientController     │   │
│  │ (Singleton)          │    │                      │   │
│  │                      │    │ Uses:                │   │
│  │ Uses:                │    │ • FileParseUtils     │   │
│  │ • Property Enum      │    │ • JAX-RS Jersey      │   │
│  │ • PropertyReader     │    │                      │   │
│  └──────────────────────┘    └──────────────────────┘   │
│                                                            │
│  ┌──────────────────────┐    ┌──────────────────────┐   │
│  │ ClassController      │    │ VariablesController  │   │
│  │                      │    │                      │   │
│  │ Uses:                │    │ Uses:                │   │
│  │ • Property Enum      │    │ • Reflection API     │   │
│  │ • Dynamic Loading    │    │                      │   │
│  └──────────────────────┘    └──────────────────────┘   │
└────────┬──────────────────────────────────────────────────┘
         │
         │ Uses
         │
┌────────▼──────────────────────────────────────────────────┐
│            MODELS LAYER                                   │
├───────────────────────────────────────────────────────────┤
│                                                            │
│  ┌──────────────────────┐  ┌──────────────────────┐     │
│  │ Request              │  │ Example              │     │
│  │ (Builder Pattern)    │  │                      │     │
│  │                      │  │ Fields:              │     │
│  │ • method, path       │  │ • All primitives     │     │
│  │ • params, headers    │  │ • Collections        │     │
│  │ • body, statusCode   │  │ • Arrays             │     │
│  └──────────────────────┘  └──────────────────────┘     │
│                                                            │
│  ┌──────────────────────┐  ┌──────────────────────┐     │
│  │ ImageMetadata        │  │ S3FileMetaData       │     │
│  │                      │  │                      │     │
│  │ • width, height      │  │ Uses:                │     │
│  │                      │  │ • S3FileContentFormat│     │
│  └──────────────────────┘  └──────────────────────┘     │
└────────┬──────────────────────────────────────────────────┘
         │
         │ Uses
         │
┌────────▼──────────────────────────────────────────────────┐
│     CONVERTORS & UTILITIES                                │
├───────────────────────────────────────────────────────────┤
│                                                            │
│  CONVERTORS:                    UTILITIES:                │
│  • JsonConvertor ────┐          • FileParseUtils        │
│  • DateConverter ─┐  │          • FileUtils              │
│  • StringConvertor ──┼─────┐    • WaitUtils             │
│  • MapConvertor ─┐   │     │    • ImageMetadataUtils    │
│  • ObjectConvertor   │     │    • AssertionConfigUtils  │
│  │                  │     │                            │
│  └──────────────────┘     │                            │
│                           │                            │
│  ENUMERATIONS:            │                            │
│  • Property ──────────────┤                            │
│  • PropertyReader         │                            │
│  • RequestParams ─────────┼─► Jackson ObjectMapper    │
│  • RequestPrefixes        │                            │
│  • S3FileContentFormat ───┘                            │
└───────────────────────────────────────────────────────────┘
```

---

## 4. KEY RELATIONSHIPS AND USAGE

### 4.1. REST Request Creation Chain
```
1. Cucumber Step
   ↓
2. GeneralApiService.createRequest(method, path)
   ↓
3. ClientController.createRestClient()
   ↓ (creates JAX-RS WebTarget)
4. GeneralApiService.addParams() → StringConvertor.convert()
   ↓ (substitutes variables and properties)
5. GeneralApiService.addHeaders() → StringConvertor.convert()
   ↓
6. GeneralApiService.sendRequest()
   ↓ (validates status with DateConverter for timestamps)
7. VariablesController.add(varName, response)
   ↓ (stores response for future use)
```

### 4.2. S3 Processing Chain
```
1. S3Definitions.getFilesFromS3()
   ↓
2. S3ServiceActions.getObject()
   ↓
3. S3ClientProvider.getClient()
   ↓
4. Parse response with S3FileContentFormat
   ↓
5. Store in VariablesController
   ↓
6. S3Definitions.filterFilesBy() uses MapConvertor
```

### 4.3. Data Transformation Chain
```
DataTable (Cucumber)
   ↓
StepArgumentConverters.convert()
   ↓
StringConvertor.convert() [substitutes @{}, ${}, date:{}, file:{}]
   ↓
PropertiesController & VariablesController
   ↓
MapConvertor.convert() [filters by prefix]
   ↓
JsonConvertor.convertMapToJsonString()
   ↓
FileParseUtils.read() [deserialization]
   ↓
Populator.populate()
   ↓
ClassController.loadClass() [dynamic class loading]
   ↓
Example / Custom Model Object
```

### 4.4. Image Processing Chain
```
ImageMetadataDefinitions
   ↓
ImageMetadataUtils.loadImageMetadata()
   ↓
Image from URL
   ↓
Extract dimensions → ImageMetadata model
   ↓
VariablesController.add()
```

---

## 5. IMPORTANT DESIGN PATTERNS

### 1. **Singleton Pattern**
- `PropertiesController` - centralized configuration management
- `S3ClientProvider` - single S3 connection per application
- `FileParseUtils` - shared ObjectMapper

### 2. **Builder Pattern**
- `Request.Builder` - convenient REST request construction

### 3. **Factory Pattern**
- `ClientController` - creating JAX-RS clients
- `S3ClientProvider` - creating S3 clients with different providers

### 4. **Template Method Pattern**
- `GeneralApiService` (abstract) - defines REST request algorithm

### 5. **Strategy Pattern**
- `StringConvertor` - different substitution strategies (@{}, ${}, date:{}, file:{})
- `S3FileContentFormat` - different S3 content formats

### 6. **Converter/Adapter Pattern**
- All classes in `convertors` package - format transformation

---

## 6. EXTERNAL DEPENDENCIES

### Framework Dependencies:
- **Cucumber** - BDD Framework (7.18.1)
- **Jersey** - REST Client (3.1.3)
- **Jackson** - JSON/XML processing (2.15.1)
- **AWS SDK** - S3 integration (2.36.2)
- **Lombok** - Code generation (1.18.32)
- **AssertJ** - Fluent assertions (3.26.3)
- **Commons Text** - String utilities (1.14.0)
- **Commons BeanUtils** - Bean operations (1.9.4)

---

## 7. TEST EXECUTION FLOW

### 7.1. Initialization
```
1. Cucumber reads .feature files
2. Calls methods from Step Definitions
3. StepArgumentConverters transforms parameters
   └─ StringConvertor substitutes variables and properties
4. Step definition methods are executed
```

### 7.2. API Test Execution
```
Scenario: Make API request
  When I make a GET request to "/api/endpoint" with:
    | param1 | value1 |
    | param2 | ${CONFIG_VALUE} |
    
Step Definition:
  1. parseDataTable() → Map<String, String>
  2. StringConvertor.convert(map) → substitutes ${} and @{}
  3. GeneralApiService.createRequest("GET", "/api/endpoint")
  4. addParams(convertedMap)
  5. sendRequest()
  6. VariablesController.add("response", result)
```

### 7.3. Assertion Test Execution
```
Scenario: Verify response
  Then I assert that variable "response.status" equals "200"
  
Step Definition:
  1. VariablesController.get("response.status")
  2. GeneralDefinitions.assertStringVariable()
  3. AssertionConfigurationUtils.createRecursiveComparisonConfig()
  4. Compare with regex support
```

---

## 8. CONFIGURATION AND ENVIRONMENTS

### Properties hierarchy:
```
common.properties (base)
  ↓
dev.properties / qa.properties / prod.properties (override)
  ↓
System environment variables (override)
```

### PropertiesController loading:
```
PropertiesController (Singleton)
  ├─ Reads common.properties
  ├─ Reads environment-specific properties (dev/qa/prod)
  ├─ Checks system properties for overrides
  └─ Provides centralized access via Property enum
```

---

## 9. USAGE EXAMPLES

### Example 1: REST API Request
```java
// Cucumber Feature
Given I have variables:
  | userId | 123 |
  | token  | ${AUTH_TOKEN} |

When I make a POST request to "/api/users/{userId}" with:
  | path:userId | @{userId} |
  | headers:Authorization | Bearer @{token} |
  | body:name | Test User |

Then I assert that response status equals 200
And I save response to variable "newUser"
```

### Example 2: S3 Operations
```java
// Cucumber Feature
When I upload file to S3:
  | bucket | test-bucket |
  | key    | data/file.json |
  | data   | {"name": "test"} |

And I get files from S3:
  | bucket | test-bucket |
  | prefix | data/ |
  | format | JSON_ARRAY |
```

### Example 3: Data Population
```java
// Cucumber Feature
Given I populate Example object:
  | id          | 123 |
  | name        | Test |
  | createdDate | date:{now+1d} |
  | items       | [item1, item2] |

Then I assert Example object field "name" equals "Test"
```

---

## 10. CONCLUSION

### Architectural Advantages:
✅ **Layering** - clear separation of responsibilities  
✅ **Reusability** - convertors, utilities, controllers  
✅ **Testability** - dependency on interfaces and abstractions  
✅ **Flexibility** - dynamic class loading, conversion strategies  
✅ **Scalability** - modular architecture  

### Key Components:
- **PropertiesController** - central configuration point
- **StringConvertor** - main substitution mechanism
- **GeneralApiService** - base REST client
- **S3ServiceActions** - AWS integration
- **VariablesController** - test state

### Data Flow:
```
Configuration → Properties/Variables → Converters → Models → 
Services → API/S3 → Response → Variables → Assertions
```



