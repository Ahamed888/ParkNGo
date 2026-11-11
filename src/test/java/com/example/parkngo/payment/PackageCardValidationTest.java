package com.example.parkngo.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PackageCardValidationTest {
    private final PaymentService service = new PaymentService(null, null);

    @Test
    void acceptsSupportedCardFormatsWithMatchingSecurityCode() {
        assertNull(service.validatePackageCard("Alex Driver", "4111 1111 1111 1111", "12/45", "123"));
        assertNull(service.validatePackageCard("Alex Driver", "5555 5555 5555 4444", "12/45", "123"));
        assertNull(service.validatePackageCard("Alex Driver", "2223 0000 4840 0011", "12/45", "123"));
        assertNull(service.validatePackageCard("Alex Driver", "3782 822463 10005", "12/45", "1234"));
        assertNull(service.validatePackageCard("Alex Driver", "6011 1111 1111 1117", "12/45", "123"));
        assertNull(service.validatePackageCard("Alex Driver", "3530 1113 3330 0000", "12/45", "123"));
        assertNull(service.validatePackageCard("Alex Driver", "3056 9309 0259 04", "12/45", "123"));
    }

    @Test
    void rejectsExtraDigitsWrongCvcAndExpiredMonth() {
        PaymentService.CardValidationError tooLong = service.validateCardFields(
                "Alex Driver", "4000000000000000006", "12/45", "123");
        assertNotNull(tooLong);
        assertEquals("cardNumber", tooLong.field());
        assertTrue(tooLong.message().contains("maximum 16 digits"));
        assertNotNull(service.validatePackageCard("Alex Driver", "41111111111111111111", "12/45", "123"));
        assertNotNull(service.validatePackageCard("Alex Driver", "4111111111111111", "12/45", "1234"));
        assertNotNull(service.validatePackageCard("Alex Driver", "378282246310005", "12/45", "123"));
        assertNotNull(service.validatePackageCard("Alex Driver", "4111111111111111", "01/20", "123"));
        assertNotNull(service.validatePackageCard("Alex Driver", "4111111111111112", "12/45", "123"));
    }
}
