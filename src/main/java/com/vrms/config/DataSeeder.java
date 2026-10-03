package com.vrms.config;

import com.vrms.model.*;
import com.vrms.repository.*;
import com.vrms.service.AuditService;
import com.vrms.service.ContractService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Runs once at startup:
 *  - creates the VRMS pickup branches if none exist;
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

    private static final String KIGALI = "Kigali Central";
    private static final String AIRPORT = "Kigali International Airport";
    private static final String KIMIHURURA = "Kimihurura";
    private static final String MUSANZE = "Musanze";
    private static final String RUBAVU = "Rubavu";
    private static final String HUYE = "Huye";

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final CustomerRepository customerRepository;
    private final RentalContractRepository contractRepository;
    private final BranchRepository branchRepository;
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

    private final Map<String, Branch> branches = new HashMap<>();

    public DataSeeder(UserRepository userRepository, VehicleRepository vehicleRepository,
                      CustomerRepository customerRepository, RentalContractRepository contractRepository,
                      BranchRepository branchRepository, PasswordEncoder passwordEncoder, AuditService audit) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.customerRepository = customerRepository;
        this.contractRepository = contractRepository;
        this.branchRepository = branchRepository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedBranches();
        User admin = seedStaff(adminEmail, adminPassword, adminName, adminJobTitle, Role.ADMIN);
        if (seedDemoData && contractRepository.count() == 0) {
            seedDemo(admin);
        }
    }

    private void seedBranches() {
        branch(KIGALI, "Kigali", "KN 5 Rd, Kigali", "+250 788 220 440");
        branch(AIRPORT, "Kigali", "Arrivals hall, Kanombe", "+250 788 220 441");
        branch(KIMIHURURA, "Kigali", "KG 7 Ave, Kimihurura", "+250 788 220 442");
        branch(MUSANZE, "Musanze", "Main Rd, Musanze town", "+250 788 220 443");
        branch(RUBAVU, "Rubavu", "Lake Kivu shore road, Gisenyi", "+250 788 220 444");
        branch(HUYE, "Huye", "Butare town centre", "+250 788 220 445");
    }

    private void branch(String name, String city, String address, String phone) {
        Branch b = branchRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> branchRepository.save(new Branch(name, city, address, phone)));
        branches.put(name, b);
    }

    User seedStaff(String email, String password, String name, String jobTitle, Role role) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("No password configured for the {} account, so it was not created. "
                    + "Add it to application-secrets.properties to sign in to the staff console.", role);
            return null;
        }
        return userRepository.findByEmailIgnoreCase(email.trim()).orElseGet(() -> {
            User user = new User();
            user.setFullName(name);
            user.setEmail(email.trim().toLowerCase());
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole(role);
            user.setJobTitle(jobTitle);
            log.info("Created {} account {}", role, user.getEmail());
            audit.log("Staff account created", "System", name + " (" + jobTitle + ") can now sign in");
            return userRepository.save(user);
        });
    }

    private void seedDemo(User admin) {
        Vehicle rav4 = vehicle("RAB123A", "Toyota RAV4", 85_000, VehicleCategory.SUV, Transmission.AUTOMATIC, FuelType.PETROL, 5, IMG_HERO, KIGALI);
        Vehicle prado = vehicle("RAE440K", "Toyota Prado", 145_000, VehicleCategory.SUV, Transmission.AUTOMATIC, FuelType.DIESEL, 7, IMG_SUV, AIRPORT);
        Vehicle tucson = vehicle("RAC902M", "Hyundai Tucson", 95_000, VehicleCategory.SUV, Transmission.AUTOMATIC, FuelType.PETROL, 5, IMG_SCENIC, KIGALI);
        Vehicle corolla = vehicle("RAD316P", "Toyota Corolla", 60_000, VehicleCategory.SEDAN, Transmission.AUTOMATIC, FuelType.PETROL, 5, IMG_ROAD, KIMIHURURA);
        corolla.setVehicleStatus(VehicleStatus.MAINTENANCE);
        Vehicle xtrail = vehicle("RAF718C", "Nissan X-Trail", 90_000, VehicleCategory.SUV, Transmission.AUTOMATIC, FuelType.PETROL, 7, IMG_HERO, MUSANZE);
        Vehicle ranger = vehicle("RAG225J", "Ford Ranger", 120_000, VehicleCategory.COMMERCIAL, Transmission.MANUAL, FuelType.DIESEL, 5, IMG_SUV, MUSANZE);
        Vehicle hiace = vehicle("RAH518B", "Toyota Hiace", 110_000, VehicleCategory.VAN, Transmission.MANUAL, FuelType.DIESEL, 14, IMG_ROAD, AIRPORT);
        Vehicle vitz = vehicle("RAA207D", "Toyota Vitz", 45_000, VehicleCategory.HATCHBACK, Transmission.AUTOMATIC, FuelType.PETROL, 5, IMG_SCENIC, HUYE);
        Set<Vehicle> added = new HashSet<>();
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
        contracts += contract(aline, prado, added, today.minusDays(2), today.plusDays(3), ContractStatus.ACTIVE, admin, AIRPORT);
        contracts += contract(patrick, tucson, added, today.minusDays(12), today.minusDays(9), ContractStatus.COMPLETED, admin, KIGALI);
        contracts += contract(eric, xtrail, added, today.minusDays(20), today.minusDays(17), ContractStatus.CANCELLED, admin, MUSANZE);
        contracts += contract(claire, ranger, added, today.plusDays(2), today.plusDays(5), ContractStatus.PENDING, null, MUSANZE);

        audit.log("Demo data loaded", "System", added.size() + " vehicles and " + contracts + " contracts added for demonstration");
        log.info("Loaded demo fleet, customers and contracts");
    }

    private Vehicle vehicle(String plate, String model, double rate, VehicleCategory category, Transmission transmission,
                            FuelType fuel, int seats, String image, String branch) {
        Vehicle v = new Vehicle();
        v.setPlateNumber(plate);
        v.setModel(model);
        v.setDailyRate(rate);
        v.setCategory(category);
        v.setTransmission(transmission);
        v.setFuelType(fuel);
        v.setSeats(seats);
        v.setImageUrl(image);
        v.setBranch(branches.get(branch));
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
    private int contract(Customer customer, Vehicle vehicle, Set<Vehicle> added, LocalDate start, LocalDate end,
                         ContractStatus status, User issuedBy, String pickup) {
        if (customer == null || !added.contains(vehicle)) {
            return 0;
        }
        RentalContract c = new RentalContract();
        c.setCustomer(customer);
        c.setVehicle(vehicle);
        c.setStartDate(start);
        c.setEndDate(end);
        c.setPickupBranch(branches.get(pickup));
        c.setContractStatus(status);
        c.setIssuedBy(issuedBy);
        c.setTotalCost(ContractService.rentalDays(start, end) * vehicle.getDailyRate());
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
