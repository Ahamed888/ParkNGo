package com.example.parkngo.discount;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DiscountServiceTest {
    @Autowired
    private DiscountService discountService;

    @Autowired
    private DiscountRepository discountRepository;

    private Discount testDiscount;

    @BeforeEach
    void setUp() {
        discountRepository.deleteAll();

        testDiscount = new Discount();
        testDiscount.setCode("TEST20");
        testDiscount.setDescription("Test discount");
        testDiscount.setDiscountValue(BigDecimal.valueOf(20));
        testDiscount.setDiscountType(Discount.DiscountType.PERCENTAGE);
        testDiscount.setValidFromDate(LocalDate.now());
        testDiscount.setValidToDate(LocalDate.now().plusDays(30));
        testDiscount.setStatus(Discount.DiscountStatus.ACTIVE);
        testDiscount = discountRepository.save(testDiscount);
    }

    @Test
    void testCreateDiscount() throws Exception {
        Discount discount = new Discount();
        discount.setCode("NEW50");
        discount.setDescription("New discount");
        discount.setDiscountValue(BigDecimal.valueOf(50));
        discount.setDiscountType(Discount.DiscountType.FIXED_AMOUNT);
        discount.setValidFromDate(LocalDate.now());
        discount.setValidToDate(LocalDate.now().plusDays(30));

        Discount created = discountService.createDiscount(discount);
        assertNotNull(created.getId());
    }

    @Test
    void testDuplicateCodePrevention() {
        Discount duplicate = new Discount();
        duplicate.setCode("TEST20");
        duplicate.setDescription("Duplicate");
        duplicate.setDiscountValue(BigDecimal.valueOf(10));

        assertThrows(Exception.class, () -> discountService.createDiscount(duplicate));
    }

    @Test
    void testPercentageDiscountCalculation() {
        BigDecimal amount = BigDecimal.valueOf(100);
        BigDecimal discount = discountService.calculateDiscount(testDiscount, amount);
        assertEquals(BigDecimal.valueOf(20), discount);
    }

    @Test
    void testGetDiscountByCode() throws Exception {
        Discount found = discountService.getDiscountByCode("TEST20");
        assertNotNull(found);
        assertEquals("TEST20", found.getCode());
    }

    @Test
    void testIsValidDiscount() {
        assertTrue(discountService.isValidDiscount("TEST20"));
    }
}
