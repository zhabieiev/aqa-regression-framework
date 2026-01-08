# language: en
Feature: Assertion

  Background:
    When example var 'result' has values:
      | booleanPrimitive         | true    |
      | aBoolean                 | false   |
      | bytePrimitive            | -128    |
      | aByte                    | 127     |
      | string                   | Example |
      | list[0].aBoolean         |         |
      | list[0].bytePrimitive    |         |
      | list[0].aByte            |         |
      | list[0].string           |         |
      | list[0].list[0].string   | 0.0     |
      | list[0].list[1].string   | 0.1     |
      | list[0].list[2].string   | 0.2     |
      | list[1].booleanPrimitive | true    |
      | list[1].aBoolean         | false   |
      | list[1].bytePrimitive    | -128    |
      | list[1].aByte            | 127     |
      | list[1].string           | Example |
      | list[1].list[0].string   | 1.0     |
      | list[2].booleanPrimitive | true    |
      | list[2].aBoolean         | false   |
      | list[2].bytePrimitive    | -128    |
      | list[2].aByte            | 127     |
      | list[2].string           | Example |
      | list[2].list[0].string   | 2.0     |
      | list[2].list[1].string   | 2.1     |

  Scenario: Assert equals
    Then var 'result' is equal to object:
      | booleanPrimitive         | true          |
      | aboolean                 | false         |
      | bytePrimitive            | -128          |
      | abyte                    | 127           |
      | string                   | Example       |
      | list[0].aboolean         | regex:null    |
      | list[0].bytePrimitive    | 0             |
      | list[0].abyte            | regex:null    |
      | list[0].string           | regex:null    |
      | list[1].booleanPrimitive | regex:true    |
      | list[1].aboolean         | regex:false   |
      | list[1].bytePrimitive    | regex:-128    |
      | list[1].abyte            | regex:127     |
      | list[1].string           | regex:Example |
      | list[2].string           | regex:.*      |

