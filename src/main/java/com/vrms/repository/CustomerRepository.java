package com.vrms.repository;

import com.vrms.model.Customer;
import com.vrms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByUser(User user);
    Optional<Customer> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndCustomerIdNot(String email, UUID customerId);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDriverLicenseNumber(String driverLicenseNumber);
    boolean existsByDriverLicenseNumberAndCustomerIdNot(String driverLicenseNumber, UUID customerId);
}
