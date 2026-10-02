package com.vrms.config;

import com.vrms.model.*;
import com.vrms.repository.CustomerRepository;
import com.vrms.repository.RentalContractRepository;
import com.vrms.repository.UserRepository;
import com.vrms.repository.VehicleRepository;
import com.vrms.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Runs once at startup:
 *  - creates the staff (ADMIN) account from vrms.admin.* if no account with that email exists yet;
 *  - when vrms.seed.demo-data=true and no contracts exist yet, adds the demo fleet, customers and
 *    contracts from the design so the screens have something to show. Rows that already exist
 *    (same plate, email or driver license) are left untouched.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String IMG_HERO = "https://images.unsplash.com/photo-1783557105881-a19f1dd0be95?auto=format&fit=crop&w=1200&q=86";
    private static final String IMG_ROAD = "https://images.unsplash.com/photo-1682773083896-95176d8aecf8?auto=format&fit=crop&w=1200&q=86";
    private static final String IMG_SUV = "https://images.unsplash.com/photo-1773423203025-d6060d1e5a8c?auto=format&fit=crop&w=1200&q=86";
    private static final String IMG_SCENIC = "https://images.unsplash.com/photo-1786702885812-6ca6d1105778?auto=format&fit=crop&w=1200&q=86";

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final CustomerRepository customerRepository;
    private final RentalContractRepository contractRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    @Value("${vrms.admin.email:}")
    private String adminEmail;
    @Value("${vrms.admin.password:}")
    private String adminPassword;
    @Value("${vrms.admin.full-name:Grace Kamanzi}")
    private String adminName;
    @Value("${vrms.admin.job-title:Operations Manager}")
    private String adminJobTitle;
    @Value("${vrms.seed.demo-data:true}")
    private boolean seedDemoData;

    public DataSeeder(UserRepository userRepository, VehicleRepository vehicleRepository,
                      CustomerRepository customerRepository, RentalContractRepository contractRepository,
                      PasswordEncoder passwordEncoder, AuditService audit) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.customerRepository = customerRepository;
        this.contractRepository = contractRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User admin = seedAdmin();
        if (seedDemoData && contractRepository.count() == 0) {
            seedDemo(admin);
        }
    }

    private User seedAdmin() {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("vrms.admin.email / vrms.admin.password are not set, so no staff account was created. "
                    + "Add them to application-secrets.properties to sign in to the staff console.");
            return null;
        }
        return userRepository.findByEmailIgnoreCase(adminEmail.trim()).orElseGet(() -> {
            User user = new User();
            user.setFullName(adminName);
            user.setEmail(adminEmail.trim().toLowerCase());
            user.setPasswordHash(passwordEncoder.encode(adminPassword));
            user.setRole(Role.ADMIN);
            user.setJobTitle(adminJobTitle);
            log.info("Created staff account {}", user.getEmail());
            audit.log("Staff account created", "System", adminName + " (" + adminJobTitle + ") can now sign in");
            return userRepository.save(user);
        });
    }

    private void seedDemo(User admin) {
        Vehicle rav4 = vehicle("RAB123A", "Toyota RAV4", 85_000, VehicleCategory.SUV, Transmission.AUTOMATIC, FuelType.PETROL, 5, IMG_HERO);
        Vehicle prado = vehicle("RAE440K", "Toyota Prado", 145_000, VehicleCategory.SUV, Transmission.AUTOMATIC, FuelType.DIESEL, 7, IMG_SUV);
        Vehicle tucson = vehicle("RAC902M", "Hyundai Tucson", 95_000, VehicleCategory.SUV, Transmission.AUTOMATIC, FuelType.PETROL, 5, IMG_SCENIC);
        Vehicle corolla = vehicle("RAD316P", "Toyota Corolla", 60_000, VehicleCategory.SEDAN, Transmission.AUTOMATIC, FuelType.PETROL, 5, IMG_ROAD);
        corolla.setVehicleStatus(VehicleStatus.MAINTENANCE);
        Vehicle xtrail = vehicle("RAF718C", "Nissan X-Trail", 90_000, VehicleCategory.SUV, Transmission.AUTOMATIC, FuelType.PETROL, 7, IMG_HERO);
        Vehicle ranger = vehicle("RAG225J", "Ford Ranger", 120_000, VehicleCategory.COMMERCIAL, Transmission.MANUAL, FuelType.DIESEL, 5, IMG_SUV);
        Vehicle hiace = vehicle("RAH518B", "Toyota Hiace", 110_000, VehicleCategory.VAN, Transmission.MANUAL, FuelType.DIESEL, 14, IMG_ROAD);
        Vehicle vitz = vehicle("RAA207D", "Toyota Vitz", 45_000, VehicleCategory.HATCHBACK, Transmission.AUTOMATIC, FuelType.PETROL, 5, IMG_SCENIC);
        java.util.Set<Vehicle> added = new java.util.HashSet<>();
        for (Vehicle v : new Vehicle[]{rav4, prado, tucson, corolla, xtrail, ranger, hiace, vitz}) {
            if (!vehicleRepository.existsByPlateNumber(v.getPlateNumber())) {
                added.add(vehicleRepository.save(v));
            }
        }

        Customer aline = customer("Aline Uwase", "aline.uwase@email.com", "+250 788 245 610", "DL-48219");
        Customer patrick = customer("Patrick Mugabo", "patrick.m@email.com", "+250 723 904 118", "DL-30184");
        Customer claire = customer("Claire Ishimwe", "claire.i@email.com", "+250 788 110 472", "DL-72014");
        Customer eric = customer("Eric Nshimiyimana", "eric.n@email.com", "+250 735 821 003", "DL-19385");

        // Contracts only between demo rows created just now, so existing records are never changed.
        LocalDate today = LocalDate.now();
        int contracts = 0;
        contracts += contract(aline, prado, added, today.minusDays(2), today.plusDays(3), ContractStatus.ACTIVE, admin, "Kigali International Airport");
        contracts += contract(patrick, tucson, added, today.minusDays(12), today.minusDays(9), ContractStatus.COMPLETED, admin, "Kigali Central");
        contracts += contract(eric, xtrail, added, today.minusDays(20), today.minusDays(17), ContractStatus.CANCELLED, admin, "Kigali Central");
        contracts += contract(claire, ranger, added, today.plusDays(2), today.plusDays(5), ContractStatus.PENDING, null, "Musanze");

        audit.log("Demo data loaded", "System", added.size() + " vehicles and " + contracts + " contracts added for demonstration");
        log.info("Loaded demo fleet, customers and contracts");
    }

    private Vehicle vehicle(String plate, String model, double rate, VehicleCategory category, Transmission transmission,
                            FuelType fuel, int seats, String image) {
        Vehicle v = new Vehicle();
        v.setPlateNumber(plate);
        v.setModel(model);
        v.setDailyRate(rate);
        v.setCategory(category);
        v.setTransmission(transmission);
        v.setFuelType(fuel);
        v.setSeats(seats);
        v.setImageUrl(image);
        return v;
    }

    /** Returns the new demo customer, or null if that email or license is already registered. */
    private Customer customer(String name, String email, String phone, String license) {
        if (customerRepository.existsByEmailIgnoreCase(email) || customerRepository.existsByDriverLicenseNumber(license)) {
            return null;
        }
        Customer c = new Customer();
        c.setFullName(name);
        c.setEmail(email);
        c.setPhoneNumber(phone);
        c.setDriverLicenseNumber(license);
        return customerRepository.save(c);
    }

    /** Keeps the vehicle status consistent with the contract, as ContractService would. */
    private int contract(Customer customer, Vehicle vehicle, java.util.Set<Vehicle> added, LocalDate start, LocalDate end,
                         ContractStatus status, User issuedBy, String pickup) {
        if (customer == null || !added.contains(vehicle)) {
            return 0;
        }
        RentalContract c = new RentalContract();
        c.setCustomer(customer);
        c.setVehicle(vehicle);
        c.setStartDate(start);
        c.setEndDate(end);
        c.setPickupLocation(pickup);
        c.setContractStatus(status);
        c.setIssuedBy(issuedBy);
        c.setTotalCost(com.vrms.service.ContractService.rentalDays(start, end) * vehicle.getDailyRate());
        contractRepository.save(c);

        if (status == ContractStatus.ACTIVE) vehicle.setVehicleStatus(VehicleStatus.RENTED);
        if (status == ContractStatus.PENDING) vehicle.setVehicleStatus(VehicleStatus.RESERVED);
        vehicleRepository.save(vehicle);
        audit.logContract(switch (status) {
            case ACTIVE -> "New contract";
            case COMPLETED -> "Returned";
            case CANCELLED -> "Cancelled";
            case PENDING -> "Booking request";
        }, c, "Demo contract for " + customer.getFullName());
        return 1;
    }
}
