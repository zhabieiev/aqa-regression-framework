package com.aqa.petstore.api.tests;

import com.aqa.petstore.api.extensions.CleanupExtension;
import com.aqa.petstore.api.models.generated.ModelApiResponse;
import com.aqa.petstore.api.models.generated.Order;
import com.aqa.petstore.api.services.StoreService;
import com.aqa.petstore.api.steps.StoreSteps;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;

import static com.aqa.petstore.api.data.OrderDataFactory.order;
import static com.aqa.petstore.api.data.TestRunId.nextLong;
import static io.qameta.allure.SeverityLevel.CRITICAL;
import static io.qameta.allure.SeverityLevel.NORMAL;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

@Epic("Petstore API")
@Feature("Store Orders")
@DisplayName("Store Order API")
@Tag("api")
@Tag("store-order")
@Tag("regression")
@ResourceLock(value = "petstore-orders", mode = READ_WRITE)
public class StoreOrderTest {

    @RegisterExtension
    final CleanupExtension cleanup = new CleanupExtension();

    private final StoreSteps storeSteps = new StoreSteps(new StoreService());

    @Test
    @Story("Create store order")
    @Severity(CRITICAL)
    @DisplayName("Creates an order with valid data")
    @Tag("smoke")
    public void shouldCreateStoreOrder() {
        final Order request = order();

        final Order response = cleanup.register(
                storeSteps.create(request),
                createdOrder -> storeSteps.delete(createdOrder.getId())
        );

        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(request);
    }

    @Test
    @Story("Get store order")
    @Severity(NORMAL)
    @DisplayName("Returns an existing order by ID")
    public void shouldGetStoreOrder() {
        final Order createdOrder = cleanup.register(
                storeSteps.create(order()),
                order -> storeSteps.delete(order.getId())
        );

        final Order response = storeSteps.get(createdOrder.getId());

        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(createdOrder);
    }

    @Test
    @Story("Get store order")
    @Severity(NORMAL)
    @DisplayName("Returns 404 for an unknown order")
    @Tag("negative")
    public void shouldReturnNotFoundForUnknownStoreOrder() {
        final ModelApiResponse response = storeSteps.get(nextLong(), HTTP_NOT_FOUND);

        final ModelApiResponse expectedResponse = new ModelApiResponse()
                .code(1)
                .type("error")
                .message("Order not found");

        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(expectedResponse);
    }

    @Test
    @Story("Delete store order")
    @Severity(CRITICAL)
    @DisplayName("Deletes an existing order")
    public void shouldDeleteStoreOrder() {
        final Order createdOrder = storeSteps.create(order());

        final ModelApiResponse response = storeSteps.delete(createdOrder.getId());

        final ModelApiResponse expectedResponse = new ModelApiResponse()
                .code(HTTP_OK)
                .type("unknown")
                .message(String.valueOf(createdOrder.getId()));

        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(expectedResponse);
    }
}