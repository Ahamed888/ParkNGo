package com.example.parkngo.discount;

import com.example.parkngo.discount.dto.DiscountRequest;
import com.example.parkngo.discount.exception.DuplicateResourceException;
import com.example.parkngo.discount.exception.ResourceInUseException;
import com.example.parkngo.discount.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DiscountService {

    private static final Logger log = LoggerFactory.getLogger(DiscountService.class);

    private final DiscountRepository discountRepository;

    public DiscountService(DiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    // --- Web-facing entry point (admin create/edit form) ---

    /**
     * Create-or-update entry point used by the single "/admin/discounts/save"
     * form: a request with no id creates a new coupon, otherwise the
     * existing coupon is updated in place. Per-field shape validation is
     * already enforced by @Valid on DiscountRequest before this method ever
     * runs; the controller also pre-checks the cross-field rules (expiry not
     * before start, percentage <= 100) so they can render as inline field
     * errors, but this method re-checks the same invariants as a final
     * safety net regardless of caller, plus the one rule that needs a
     * database lookup: the coupon code must be unique.
     */
    public Discount saveDiscount(DiscountRequest request) {
        String normalizedCode = request.getCouponCode() == null ? null : request.getCouponCode().trim().toUpperCase();
        request.setCouponCode(normalizedCode);
        assertInvariants(request);

        Optional<Discount> sameCode = discountRepository.findByCode(normalizedCode);

        if (request.getId() == null) {
            if (sameCode.isPresent()) {
                throw new DuplicateResourceException("Discount code " + normalizedCode + " already exists");
            }
            Discount discount = new Discount();
            request.applyTo(discount);
            Discount saved = discountRepository.save(discount);
            log.info("Created discount coupon id={} code='{}'", saved.getId(), saved.getCode());
            return saved;
        }

        Discount existing = discountRepository.findById(request.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with id: " + request.getId()));

        if (sameCode.isPresent() && !sameCode.get().getId().equals(existing.getId())) {
            throw new DuplicateResourceException("Discount code " + normalizedCode + " already exists");
        }

        request.applyTo(existing);
        Discount saved = discountRepository.save(existing);
        log.info("Updated discount coupon id={} code='{}'", saved.getId(), saved.getCode());
        return saved;
    }

    private void assertInvariants(DiscountRequest request) {
        if (request.getStartDate() != null && request.getExpiryDate() != null
                && request.getExpiryDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("Expiry date cannot be before the start date");
        }
        if (request.getDiscountType() == Discount.DiscountType.PERCENTAGE
                && request.getValue() != null
                && request.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("A percentage discount cannot exceed 100%");
        }
        if (request.getMaxUses() != null && request.getMaxUsesPerCustomer() != null
                && request.getMaxUsesPerCustomer() > request.getMaxUses()) {
            throw new IllegalArgumentException("Per-customer usage cap cannot exceed the global usage cap");
        }
        if (request.getAppliesTo() == Discount.AppliesTo.OFF_PEAK_ONLY
                && (request.getOffPeakStartHour() == null || request.getOffPeakEndHour() == null)) {
            throw new IllegalArgumentException("Off-peak start and end hours are required for an off-peak-only code");
        }
    }

    public Discount toggleStatus(Long id) {
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with id: " + id));
        discount.setActive(!discount.isActive());
        Discount saved = discountRepository.save(discount);
        log.info("Toggled discount coupon id={} to active={}", saved.getId(), saved.isActive());
        return saved;
    }

    public Discount getDiscountByCode(String code) throws Exception {
        return discountRepository.findByCode(code)
                .orElseThrow(() -> new Exception("Discount not found with code: " + code));
    }

    public Discount getDiscountById(Long id) {
        return discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with id: " + id));
    }

    public List<Discount> getAllDiscounts() {
        return discountRepository.findAll();
    }

    public List<Discount> getActiveDiscounts() {
        return discountRepository.findByStatus(Discount.DiscountStatus.ACTIVE);
    }

    public boolean isValidDiscount(String code) {
        return getDiscountValidationMessage(code) == null;
    }

    /** Returns a customer-safe reason when a code cannot currently be used, or null when valid. */
    public String getDiscountValidationMessage(String code) {
        if (code == null || code.trim().isEmpty()) return "Enter a discount code.";
        Discount discount = discountRepository.findByCode(code.trim().toUpperCase()).orElse(null);
        if (discount == null) return "This discount code is not recognized.";
        if (discount.getStatus() != Discount.DiscountStatus.ACTIVE) return "This discount code is inactive.";
        LocalDate today = LocalDate.now();
        if (discount.getValidFromDate() != null && today.isBefore(discount.getValidFromDate())) {
            return "This discount code is not active yet.";
        }
        if (discount.getValidToDate() != null && today.isAfter(discount.getValidToDate())) {
            return "This discount code has expired.";
        }
        return null;
    }

    public BigDecimal calculateDiscount(Discount discount, BigDecimal amount) {
        if (discount.getDiscountType() == Discount.DiscountType.PERCENTAGE) {
            return amount.multiply(discount.getDiscountValue()).divide(BigDecimal.valueOf(100));
        } else {
            return discount.getDiscountValue().min(amount);
        }
    }

    /**
     * Hard delete, guarded against removing a coupon that reservations still
     * reference (the FK on reservations.discount_id protects us here rather
     * than a manual lookup, since Reservation/ReservationRepository belong to
     * a teammate's module). Prefer deactivating a coupon that is in use.
     */
    public void deleteDiscount(Long id) {
        try {
            discountRepository.deleteById(id);
            discountRepository.flush();
            log.info("Deleted discount coupon id={}", id);
        } catch (DataIntegrityViolationException e) {
            log.warn("Blocked delete of discount coupon id={} - still referenced by existing reservations", id);
            throw new ResourceInUseException("This discount code has been used on existing customer reservations and cannot be deleted.");
        }
    }

    // --- Legacy entity-based API (kept for backward compatibility and unit
    // test coverage; no longer used by the web layer, which now goes through
    // saveDiscount(DiscountRequest) above) ---

    public Discount createDiscount(Discount discount) throws Exception {
        normalize(discount);
        if (discountRepository.existsByCode(discount.getCode())) {
            throw new Exception("Discount code " + discount.getCode() + " already exists");
        }
        validate(discount);
        return discountRepository.save(discount);
    }

    public Discount saveDiscount(Discount submitted) throws Exception {
        normalize(submitted);
        validate(submitted);

        if (submitted.getId() == null) {
            if (discountRepository.existsByCode(submitted.getCode())) {
                throw new Exception("Discount code " + submitted.getCode() + " already exists");
            }
            return discountRepository.save(submitted);
        }

        Discount existing = discountRepository.findById(submitted.getId())
                .orElseThrow(() -> new Exception("Discount not found"));

        discountRepository.findByCode(submitted.getCode()).ifPresent(other -> {
            if (!other.getId().equals(existing.getId())) {
                throw new IllegalStateException("Discount code " + submitted.getCode() + " already exists");
            }
        });

        existing.setCode(submitted.getCode());
        existing.setDescription(submitted.getDescription());
        existing.setDiscountValue(submitted.getDiscountValue());
        existing.setDiscountType(submitted.getDiscountType());
        existing.setValidFromDate(submitted.getValidFromDate());
        existing.setValidToDate(submitted.getValidToDate());
        existing.setActive(submitted.isActive());
        return discountRepository.save(existing);
    }

    private void normalize(Discount discount) {
        if (discount.getCode() != null) {
            discount.setCode(discount.getCode().trim().toUpperCase());
        }
    }

    private void validate(Discount discount) throws Exception {
        if (discount.getCode() == null || discount.getCode().isBlank()) {
            throw new Exception("Discount code is required");
        }
        if (discount.getValidFromDate() != null && discount.getValidToDate() != null
                && discount.getValidToDate().isBefore(discount.getValidFromDate())) {
            throw new Exception("Expiry date cannot be before the start date");
        }
        if (discount.getDiscountValue() == null || discount.getDiscountValue().signum() < 0) {
            throw new Exception("Discount value must be zero or greater");
        }
        if (discount.getDiscountType() == Discount.DiscountType.PERCENTAGE
                && discount.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new Exception("A percentage discount cannot exceed 100%");
        }
    }
}
