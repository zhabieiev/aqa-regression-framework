package com.aqa.core.definitions;

import com.aqa.core.controllers.VariablesController;
import com.aqa.core.models.Example;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.assertj.core.api.AssertionsForClassTypes;

import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;

import static com.aqa.core.Populator.populate;
import static java.lang.String.valueOf;
import static java.time.Clock.systemUTC;
import static java.time.ZoneId.SHORT_IDS;
import static java.time.ZonedDateTime.now;
import static java.time.format.DateTimeFormatter.ofPattern;

public record ExampleDefinitions(VariablesController variablesController) {

    @Given("example var {string} has value {string}")
    public void varHasValue(String var, String value) {
        variablesController.setVar(var, value);
    }

    @Given("example var {string} has values:")
    public void dataTableHasValue(String var, Map<String, String> map) {
        variablesController.setVar(var, populate(map, Example.class));
    }

    @Then("example primitives must be properly converted")
    public void primitivesMustBeProperlyConverted() {
        final Example expected = new Example();
        expected.setBooleanPrimitive(true);
        expected.setABoolean(false);
        expected.setBytePrimitive((byte) -128);
        expected.setAByte((byte) 127);
        expected.setShortPrimitive((short) -32768);
        expected.setAShort((short) 32767);
        expected.setIntPrimitive(-2147483648);
        expected.setInteger(2147483647);
        expected.setLongPrimitive(-9223372036854775808L);
        expected.setALong(9223372036854775807L);
        expected.setFloatPrimitive(0.0F);
        expected.setAFloat(0.0F);
        expected.setDoublePrimitive(0.0D);
        expected.setADouble(0.0D);
        expected.setArray(new Integer[]{1, 2});
        AssertionsForClassTypes.assertThat(variablesController.getVar("result"))
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Then("example string must be properly converted")
    public void stringMustBeProperlyConverted() {
        final Example expected = new Example();
        expected.setList(new ArrayList<>());
        expected.getList().add(0, new Example());
        expected.getList().get(0).setString(null);

        expected.getList().add(1, new Example());
        expected.getList().get(1).setString("");

        expected.getList().add(2, new Example());
        expected.getList().get(2).setString("PrefixPropertySuffix");

        expected.getList().add(3, new Example());
        expected.getList().get(3).setString("Property+https://aqa.com");

        expected.getList().add(4, new Example());
        expected.getList().get(4).setString("PrefixVariableSuffix");

        expected.getList().add(5, new Example());
        expected.getList()
                .get(5)
                .setString("PrefixFileStartPropertyVariable%sFileEndSuffix".formatted(
                        now(systemUTC()).truncatedTo(ChronoUnit.HOURS)));

        expected.getList().add(6, new Example());
        expected.getList()
                .get(6)
                .setString("FileStartPropertyVariable%sFileEndFileStartPropertyVariable%sFileEnd".formatted(
                        now(systemUTC()).truncatedTo(ChronoUnit.HOURS),
                        now(systemUTC()).truncatedTo(ChronoUnit.HOURS)));

        expected.getList().add(7, new Example());
        expected.getList()
                .get(7)
                .setString("PropertyVariable%sFileStartPropertyVariable%sFileEnd".formatted(
                        now(systemUTC()).truncatedTo(ChronoUnit.HOURS),
                        now(systemUTC()).truncatedTo(ChronoUnit.HOURS)));

        expected.getList().add(8, new Example());
        expected.getList()
                .get(8)
                .setString(valueOf(now(systemUTC()).plusHours(1)
                        .truncatedTo(ChronoUnit.HOURS)
                        .toInstant()
                        .toEpochMilli()));

        expected.getList().add(9, new Example());
        expected.getList().get(9).setString("now-3w");

        expected.getList().add(10, new Example());
        expected.getList()
                .get(10)
                .setString(valueOf(now(ZoneId.of("EST", SHORT_IDS)).minusMinutes(2).truncatedTo(ChronoUnit.MINUTES)));

        expected.getList().add(11, new Example());
        expected.getList().get(11).setString(now(systemUTC()).minusDays(1).format(ofPattern("yyyy-MM-dd HH:00:00")));

        expected.getList().add(12, new Example());
        expected.getList()
                .get(12)
                .setString("Prefix%sSuffix %s".formatted(now(systemUTC()).minusDays(1).truncatedTo(ChronoUnit.DAYS),
                        now(systemUTC()).truncatedTo(ChronoUnit.DAYS)));

        AssertionsForClassTypes.assertThat(variablesController.getVar("result"))
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }
}
