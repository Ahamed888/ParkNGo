package com.example.parkngo.user;

import com.example.parkngo.facility.ParkingFacility;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Staff")
@PrimaryKeyJoinColumn(name = "UserID")
@DiscriminatorValue("STAFF")
public class Staff extends User {

    @Column(name = "EmployeeID", length = 30)
    private String employeeId;

    @Column(name = "Shift", length = 30)
    private String shift;

    /**
     * A staff account operates one facility at a time.  This deliberately
     * lives on Staff (rather than being inferred from a request parameter) so
     * every facility-scoped operation can be authorized by the server.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "FacilityID")
    private ParkingFacility assignedFacility;

    public Staff() {
        super();
    }

    public Staff(Long id, String fullName, String email, String phone, String password, 
                 Role role, UserStatus status, LocalDateTime createdAt, LocalDateTime updatedAt,
                 String employeeId, String shift) {
        super(id, fullName, email, phone, password, role, status, createdAt, updatedAt);
        this.employeeId = employeeId;
        this.shift = shift;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getShift() {
        return shift;
    }

    public void setShift(String shift) {
        this.shift = shift;
    }

    public ParkingFacility getAssignedFacility() {
        return assignedFacility;
    }

    public void setAssignedFacility(ParkingFacility assignedFacility) {
        this.assignedFacility = assignedFacility;
    }
}
