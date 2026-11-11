package com.example.parkngo.discount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MembershipBenefitRepository extends JpaRepository<MembershipBenefit, Long> {
    List<MembershipBenefit> findByMembershipPackage_IdOrderByIdAsc(Long packageId);
}
