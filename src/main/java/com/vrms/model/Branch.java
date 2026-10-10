package com.vrms.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** A VRMS office where vehicles are kept and customers pick them up, e.g. "Kigali International Airport". */
@Entity
@Table(name = "branches")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "branch_id", updatable = false, nullable = false)
    private UUID branchId;

    @NotBlank(message = "Branch name is required")
    @Size(max = 80)
    @Column(unique = true, nullable = false)
    private String name;

    @NotBlank(message = "City is required")
    @Size(max = 60)
    @Column(nullable = false)
    private String city;

    @Size(max = 150)
    private String address;

    @Size(max = 20)
    private String phoneNumber;

    public Branch() {}

    public Branch(String name, String city, String address, String phoneNumber) {
        this.name = name;
        this.city = city;
        this.address = address;
        this.phoneNumber = phoneNumber;
    }

    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? null : name.trim(); }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city == null ? null : city.trim(); }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}
