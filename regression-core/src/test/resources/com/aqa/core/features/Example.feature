# language: en
Feature: Example

  Scenario: Primitives conversion
    When example var 'result' has values:
      | booleanPrimitive | true                 |
      | aBoolean         | false                |
      | bytePrimitive    | -128                 |
      | aByte            | 127                  |
      | shortPrimitive   | -32768               |
      | aShort           | 32767                |
      | intPrimitive     | -2147483648          |
      | integer          | 2147483647           |
      | longPrimitive    | -9223372036854775808 |
      | aLong            | 9223372036854775807  |
      | floatPrimitive   | 0.0f                 |
      | aFloat           | 0.0f                 |
      | doublePrimitive  | 0.0d                 |
      | aDouble          | 0.0d                 |
      | array            | [1,2]                |
    Then example primitives must be properly converted

  Scenario: String conversion
    Given example var 'variable' has value 'Variable'
    And example var 'result' has values:
                             # Null:
      | list[0].string  |                                                                                                |
                             # Empty string:
      | list[1].string  | ""                                                                                             |
                             # Property inside string
      | list[2].string  | Prefix${env.property}Suffix                                                                    |
                             # Property and parent property
      | list[3].string  | ${env.property}+${url.aqa}                                                                     |
                             # Variable inside string
      | list[4].string  | Prefix@{variable}Suffix                                                                        |
                             # File inside string. File contains property and variable
      | list[5].string  | Prefixfile:{regression-core/src/test/resources/files/Example.txt}Suffix                        |
                             # Multiple files
      | list[6].string  | file:{regression-core/${path.file}/Example.txt}file:{regression-core/${path.file}/Example.txt} |
                             # Property+variable+date+file
      | list[7].string  | ${env.property}@{variable}date:{now-0h/h}file:{regression-core/${path.file}/Example.txt}       |
                             # DateTime epoch format
      | list[8].string  | date:{now+1h/h(epoch)}                                                                         |
                             # DateTime as string
      | list[9].string  | date:{now-3w(asString)}                                                                        |
                             # DateTime with timeZone
      | list[10].string | date:{now-2m/m (EST)}                                                                          |
                             # Formatted DateTime
      | list[11].string | date:{now-1d (yyyy-MM-dd HH:00:00)}                                                            |
                             # DateTime inside string, two DateTime variables
      | list[12].string | Prefixdate:{now-1d/d}Suffix date:{now+0d/d}                                                    |
    Then example string must be properly converted

