package com.aqa.core.models;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter

public class Example {
    private boolean booleanPrimitive;
    private Boolean aBoolean;
    private byte bytePrimitive;
    private Byte aByte;
    private short shortPrimitive;
    private Short aShort;
    private int intPrimitive;
    private Integer integer;
    private long longPrimitive;
    private Long aLong;
    private float floatPrimitive;
    private Float aFloat;
    private double doublePrimitive;
    private Double aDouble;
    private String string;
    private List<Example> list;
    private Set<Example> set;
    private Map<String, Example> map;
    private Integer[] array;
}
