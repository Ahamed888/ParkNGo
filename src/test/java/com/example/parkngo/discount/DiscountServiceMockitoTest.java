package com.example.parkngo.discount;

import com.example.parkngo.discount.dto.DiscountRequest;
import com.example.parkngo.discount.exception.DuplicateResourceException;
import com.example.parkngo.discount.exception.ResourceInUseException;
import com.example.parkngo.discount.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DiscountServiceMockitoTest.java
 * Pure Mockito unit tests (no Spring context, no database) for the newer
 * DTO-based DiscountService.saveDiscount(DiscountRequest) entry point used
 * by the admin form, plus the guarded delete. This complements the existing
 * DiscountServiceTest, which is a @SpringBootTest integration test covering
 * the legacy entity-based API (createDiscount, calculateDiscount,
 * isValidDiscount) - that file is left untouched, this one is net new.
 */
@ExtendWith(MockitoExtension.class)
class DiscountServiceMockitoTest {

    @Mock
    private DiscountRepository discountRepository;

    @InjectMocks
    private DiscountService discountService;

    private DiscountRequest newDiscountRequest() {
        DiscountRequest request = new DiscountRequest();
        request.setCouponCode("summer2026");
        request.setDiscountType(Discount.DiscountType.PERCENTAGE);
        request.setValue(BigDecimal.valueOf(15));
        request.setStartDate(LocalDate.now());
        request.setExpiryDate(LocalDate.now().plusDays(30));
        request.setDescription("Summer promo");
        request.setActive(true);
        return request;
    }

    @Test
    void saveDiscount_create_normalizesCodeToUppercaseAndPersists() {
        DiscountRequest request = newDiscountRequest();
        when(discountRepository.findByCode("SUMMER2026")).thenReturn(Optional.empty());
        when(discountRepository.save(any(Discount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Discount saved = discountService.saveDiscount(request);

        assertEquals("SUMMER2026", saved.getCode());
        assertEquals(Discount.DiscountType.PERCENTAGE, saved.getDiscountType());
        assertTrue(saved.isActive());
    }

    @Test
    void saveDiscount_create_rejectsDuplicateCode() {
        DiscountRequest request = newDiscountRequest();
        Discount existing = new Discount();
        existing.setId(1L);
        existing.setCode("SUMMER2026");
        when(discountRepository.findByCode("SUMMER2026")).thenReturn(Optional.of(existing));

        assertThrows(DuplicateResourceException.class, () -> discountService.saveDiscount(request));
        verify(discountRepository, never()).save(any(Discount.class));
    }

    @Test
    void saveDiscount_rejectsExpiryBeforeStartDate() {
        DiscountRequest request = newDiscountRequest();
        request.setStartDate(LocalDate.now());
        request.setExpiryDate(LocalDate.now().minusDays(1));

        assertThrows(IllegalArgumentException.class, () -> discountService.saveDiscount(request));
        verify(discountRepository, never()).save(any(Discount.class));
    }

    @Test
    void saveDiscount_rejectsPercentageOver100() {
        DiscountRequest request = newDiscountRequest();
        request.setDiscountType(Discount.DiscountType.PERCENTAGE);
        request.setValue(BigDecimal.valueOf(150));

        assertThrows(IllegalArgumentException.class, () -> discountService.saveDiscount(request));
        verify(discountRepository, never()).save(any(Discount.class));
    }

    @Test
    void saveDiscount_allowsFixedAmountOver100() {
        DiscountRequest request = newDiscountRequest();
        request.setDiscountType(Discount.DiscountType.FIXED_AMOUNT);
        request.setValue(BigDecimal.valueOf(150));
        when(discountRepository.findByCode("SUMMER2026")).thenReturn(Optional.empty());
        when(discountRepository.save(any(Discount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> discountService.saveDiscount(request));
    }

    @Test
    void saveDiscount_update_throwsWhenTargetMissing() {
        DiscountRequest request = newDiscountRequest();
        request.setId(404L);
        when(discountRepository.findByCode("SUMMER2026")).thenReturn(Optional.empty());
        when(discountRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> discountService.saveDiscount(request));
    }

    @Test
    void deleteDiscount_translatesForeignKeyViolationIntoResourceInUse() {
        doThrow(new DataIntegrityViolationException("FK violation")).when(discountRepository).deleteById(1L);

        assertThrows(ResourceInUseException.class, () -> discountService.deleteDiscount(1L));
    }

    @Test
    void calculateDiscount_calculatesPercentageAndFixedAmounts() {
        Discount percentage = new Discount();
        percentage.setDiscountType(Discount.DiscountType.PERCENTAGE);
        percentage.setDiscountValue(BigDecimal.valueOf(15));

        Discount fixed = new Discount();
        fixed.setDiscountType(Discount.DiscountType.FIXED_AMOUNT);
        fixed.setDiscountValue(BigDecimal.valueOf(500));

        assertEquals(0, BigDecimal.valueOf(30).compareTo(
                discountService.calculateDiscount(percentage, BigDecimal.valueOf(200))));
        assertEquals(0, BigDecimal.valueOf(200).compareTo(
                discountService.calculateDiscount(fixed, BigDecimal.valueOf(200))));
    }
}
