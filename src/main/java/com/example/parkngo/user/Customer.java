package com.example.parkngo.user;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Customer")
@PrimaryKeyJoinColumn(name = "UserID")
@DiscriminatorValue("CUSTOMER")
public class Customer extends User {

    @Column(name = "LoyaltyPoints", nullable = false)
    private Integer loyaltyPoints = 0;

    @Column(name = "MembershipTier", nullable = false, length = 30)
    private String membershipTier = "BRONZE";

    public Customer() {
        super();
    }

    public Customer(Long id, String fullName, String email, String phone, String password, 
                    Role role, UserStatus status, LocalDateTime createdAt, LocalDateTime updatedAt,
                    Integer loyaltyPoints, String membershipTier) {
        super(id, fullName, email, phone, password, role, status, createdAt, updatedAt);
        this.loyaltyPoints = loyaltyPoints != null ? loyaltyPoints : 0;
        this.membershipTier = membershipTier != null ? membershipTier : "BRONZE";
    }

    public Integer getLoyaltyPoints() {
        return loyaltyPoints;
    }

    public void setLoyaltyPoints(Integer loyaltyPoints) {
        this.loyaltyPoints = loyaltyPoints != null ? loyaltyPoints : 0;
    }

    public String getMembershipTier() {
        return membershipTier;
    }

    public void setMembershipTier(String membershipTier) {
        this.membershipTier = membershipTier != null ? membershipTier : "BRONZE";
    }
}
