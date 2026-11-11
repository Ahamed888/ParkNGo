package com.example.parkngo.subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;
public interface BenefitUsageRepository extends JpaRepository<BenefitUsage, Long> {
    Optional<BenefitUsage> findBySubscription_IdAndBenefit_IdAndPeriodStart(Long subscriptionId, Long benefitId, LocalDate periodStart);
}
