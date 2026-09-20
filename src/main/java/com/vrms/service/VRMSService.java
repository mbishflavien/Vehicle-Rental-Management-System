package com.vrms.service;

import com.vrms.model.*;
import com.vrms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class VRMSService {

    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private RentalContractRepository contractRepository;

    // --- VEHICLE CRUD ---
    public List<Vehicle> getAllVehicles() { return vehicleRepository.findAll(); }
    public Vehicle getVehicleById(UUID id) { return vehicleRepository.findById(id).orElse(null); }
    public Vehicle saveVehicle(Vehicle vehicle) { return vehicleRepository.save(vehicle); }
    public void deleteVehicle(UUID id) { vehicleRepository.deleteById(id); }

    // --- CUSTOMER CRUD ---
    public List<Customer> getAllCustomers() { return customerRepository.findAll(); }
    public Customer getCustomerById(UUID id) { return customerRepository.findById(id).orElse(null); }
    public Customer saveCustomer(Customer customer) { return customerRepository.save(customer); }
    public void deleteCustomer(UUID id) { customerRepository.deleteById(id); }

    // --- CONTRACT CRUD & BUSINESS LOGIC ---
    public List<RentalContract> getAllContracts() { return contractRepository.findAll(); }
    public RentalContract getContractById(UUID id) { return contractRepository.findById(id).orElse(null); }

    public RentalContract createContract(RentalContract contract) {
        Vehicle vehicle = vehicleRepository.findById(contract.getVehicle().getVehicleId())
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));
        Customer customer = customerRepository.findById(contract.getCustomer().getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        if (vehicle.getVehicleStatus() != VehicleStatus.AVAILABLE) {
            throw new RuntimeException("Vehicle is not available for rental");
        }

        long days = ChronoUnit.DAYS.between(contract.getStartDate(), contract.getEndDate());
        if (days <= 0) days = 1;

        contract.setTotalCost(days * vehicle.getDailyRate());
        contract.setContractStatus(ContractStatus.ACTIVE);

        vehicle.setVehicleStatus(VehicleStatus.RENTED);
        vehicleRepository.save(vehicle);

        contract.setVehicle(vehicle);
        contract.setCustomer(customer);
        return contractRepository.save(contract);
    }

    public void deleteContract(UUID id) {
        RentalContract contract = contractRepository.findById(id).orElse(null);
        if (contract != null) {
            Vehicle vehicle = contract.getVehicle();
            vehicle.setVehicleStatus(VehicleStatus.AVAILABLE);
            vehicleRepository.save(vehicle);
            contractRepository.deleteById(id);
        }
    }
}