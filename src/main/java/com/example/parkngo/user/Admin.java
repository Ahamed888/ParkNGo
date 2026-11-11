package com.example.parkngo.user;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Admin")
@PrimaryKeyJoinColumn(name = "UserID")
@DiscriminatorValue("ADMIN")
public class Admin extends User {

    @Column(name = "Department", length = 50)
    private String department = "SYSTEM_ADMINISTRATION";

    public Admin() {
        super();
    }

    public Admin(Long id, String fullName, String email, String phone, String password, 
                 Role role, UserStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        super(id, fullName, email, phone, password, role, status, createdAt, updatedAt);
    }

    public Admin(Long id, String fullName, String email, String phone, String password, 
                 Role role, UserStatus status, LocalDateTime createdAt, LocalDateTime updatedAt,
                 String department) {
        super(id, fullName, email, phone, password, role, status, createdAt, updatedAt);
        this.department = department;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }
}
