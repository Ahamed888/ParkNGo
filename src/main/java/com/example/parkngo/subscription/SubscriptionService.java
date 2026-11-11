package com.example.parkngo.subscription;

import com.example.parkngo.discount.Package;
import com.example.parkngo.payment.Payment;
import com.example.parkngo.payment.PaymentRepository;
import com.example.parkngo.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;

/**
 * SubscriptionService.java
 * Turns a Package purchase into an active membership Subscription: computes
 * the validity window and issues a unique simulated gate access PIN.
 */
@Service
@Transactional
public class SubscriptionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, PaymentRepository paymentRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
    }

    /** Creates an unpaid membership request. Benefits become usable only through activateAfterPayment. */
    public Subscription subscribe(User customer, Package pkg) throws Exception {
        if (pkg.getStatus() != Package.PackageStatus.ACTIVE) {
            throw new Exception("This package is no longer available for subscription");
        }

        // Block buying a second active or pending pass of the same package. A pass
        // whose end date has already passed is lazily flipped to EXPIRED
        // right here (rather than only the next time it's read) so a
        // genuinely lapsed membership never blocks a legitimate repurchase.
        List<Subscription> activeForPackage = subscriptionRepository
                .findByCustomer_IdAndMembershipPackage_IdAndStatus(customer.getId(), pkg.getId(), Subscription.SubscriptionStatus.ACTIVE);
        List<Subscription> pendingForPackage = subscriptionRepository
                .findByCustomer_IdAndMembershipPackage_IdAndStatus(customer.getId(), pkg.getId(), Subscription.SubscriptionStatus.PENDING);
        activeForPackage.forEach(this::refreshExpiry);
        boolean alreadyActive = activeForPackage.stream()
                .anyMatch(s -> s.getStatus() == Subscription.SubscriptionStatus.ACTIVE);
        if (alreadyActive || !pendingForPackage.isEmpty()) {
            throw new Exception("You already have an active or pending " + pkg.getName() + " pass");
        }

        int validityDays = pkg.getValidityDays() != null && pkg.getValidityDays() > 0 ? pkg.getValidityDays() : 30;

        Subscription subscription = new Subscription();
        subscription.setCustomer(customer);
        subscription.setMembershipPackage(pkg);
        subscription.setStartDate(LocalDate.now());
        subscription.setEndDate(LocalDate.now().plusDays(validityDays));
        subscription.setStatus(Subscription.SubscriptionStatus.PENDING);
        subscription.setGateAccessPin(generateGateAccessPin());

        // Snapshot pattern: copy the package's name/price/visit-count onto
        // this record now, so a later admin edit or discontinuation of the
        // source Package can never retroactively change what was bought.
        subscription.setPackageNameSnap(pkg.getName());
        subscription.setPricePaidSnap(pkg.getPrice());
        subscription.setVisitsRemaining(pkg.getVisitCount());

        return subscriptionRepository.save(subscription);
    }

    public Subscription activateAfterPayment(Long subscriptionId, Long customerId) throws Exception {
        Subscription subscription = subscriptionRepository.findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new Exception("Membership request not found"));
        if (!subscription.getCustomer().getId().equals(customerId)) throw new Exception("You do not own this membership request");
        if (subscription.getStatus() != Subscription.SubscriptionStatus.PENDING) throw new Exception("Membership is not awaiting payment");
        boolean settled = paymentRepository.findBySubscriptionId(subscriptionId).stream()
                .anyMatch(payment -> payment.getStatus().isSettled());
        if (!settled) throw new Exception("Membership cannot be activated before payment succeeds");
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        return subscriptionRepository.save(subscription);
    }

    public void cancelSubscription(Long id, Long customerId) throws Exception {
        Subscription subscription = subscriptionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new Exception("Subscription not found"));

        if (!subscription.getCustomer().getId().equals(customerId)) {
            throw new Exception("You do not own this subscription");
        }
        if (subscription.getStatus() != Subscription.SubscriptionStatus.ACTIVE
                && subscription.getStatus() != Subscription.SubscriptionStatus.PENDING) {
            throw new Exception("Only an active or pending pass can be cancelled");
        }

        if (subscription.getStatus() == Subscription.SubscriptionStatus.PENDING) {
            paymentRepository.findBySubscriptionId(id).stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.PENDING)
                    .forEach(p -> {
                        p.setStatus(Payment.PaymentStatus.FAILED);
                        p.setFailureReason("Pending package purchase cancelled");
                        paymentRepository.save(p);
                    });
        }
        subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
        subscriptionRepository.save(subscription);
    }

    /**
     * If a deleted/discontinued package is later reactivated existing buyers
     * still keep their pass until it naturally expires (per proposal section
     * 6.4) - this method only ever flips ACTIVE -> EXPIRED once the end date
     * has passed, it never touches CANCELLED passes.
     */
    private void refreshExpiry(Subscription subscription) {
        try {
            if (subscription.getStatus() == Subscription.SubscriptionStatus.ACTIVE
                    && subscription.getEndDate() != null
                    && subscription.getEndDate().isBefore(LocalDate.now())) {
                subscription.setStatus(Subscription.SubscriptionStatus.EXPIRED);
                subscriptionRepository.save(subscription);
            }
        } catch (Exception ignored) {
        }
    }

    public List<Subscription> getCustomerSubscriptions(Long customerId) {
        if (customerId == null) return List.of();
        try {
            List<Subscription> subscriptions;
            try {
                subscriptions = subscriptionRepository.findByCustomer_IdOrderByIdDesc(customerId);
            } catch (Exception ex) {
                subscriptions = subscriptionRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId);
            }
            subscriptions.forEach(this::refreshExpiry);
            return subscriptions;
        } catch (Exception e) {
            return List.of();
        }
    }

    public Subscription getSubscriptionById(Long id) throws Exception {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new Exception("Subscription not found"));
        refreshExpiry(subscription);
        return subscription;
    }

    /**
     * The customer's currently usable membership pass, if any - read-only,
     * used to preview the pass's discount on the reservation form before the
     * customer books (CheckoutService is still the one that actually decides
     * whether it gets applied, and against what, at booking time).
     */
    public Subscription getActiveMembership(Long customerId) {
        if (customerId == null) return null;
        try {
            return getCustomerSubscriptions(customerId).stream()
                    .filter(s -> s.getStatus() == Subscription.SubscriptionStatus.ACTIVE)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Read-only pricing candidates; a preview must never consume a membership visit. */
    public List<Subscription> getParkingMemberships(Long customerId) {
        LocalDate today = LocalDate.now();
        return getCustomerSubscriptions(customerId).stream()
                .filter(pass -> pass.getStatus() == Subscription.SubscriptionStatus.ACTIVE)
                .filter(pass -> pass.getStartDate() == null || !pass.getStartDate().isAfter(today))
                .filter(pass -> pass.getEndDate() == null || !pass.getEndDate().isBefore(today))
                .filter(pass -> pass.getVisitsRemaining() == null || pass.getVisitsRemaining() > 0)
                .filter(pass -> pass.getMembershipPackage() != null)
                .filter(pass -> pass.getMembershipPackage().getParkingDiscountPercentage().signum() > 0)
                .sorted(java.util.Comparator.comparing(Subscription::getEndDate,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
    }

    private String generateGateAccessPin() {
        String pin;
        do {
            int number = 1000 + RANDOM.nextInt(9000);
            pin = "GATE-PASS-" + number;
        } while (subscriptionRepository.existsByGateAccessPin(pin));
        return pin;
    }
}
