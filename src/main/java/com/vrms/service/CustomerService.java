package com.vrms.service;

import com.vrms.dto.ProfileRequest;
import com.vrms.exception.ApiException;
import com.vrms.model.Customer;
import com.vrms.model.User;
import com.vrms.repository.CustomerRepository;
import com.vrms.repository.RentalContractRepository;
import com.vrms.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final RentalContractRepository contractRepository;
    private final UserRepository userRepository;
    private final DocumentService documentService;
    private final AuditService audit;

    public CustomerService(CustomerRepository customerRepository, RentalContractRepository contractRepository,
                           UserRepository userRepository, DocumentService documentService, AuditService audit) {
        this.customerRepository = customerRepository;
        this.contractRepository = contractRepository;
        this.userRepository = userRepository;
        this.documentService = documentService;
        this.audit = audit;
    }

    public List<Customer> getAll() {
        return customerRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Customer getById(UUID id) {
        return customerRepository.findById(id).orElseThrow(() -> ApiException.notFound("Customer"));
    }

    /** BR-01: duplicate emails and driver licenses are rejected with a message on the right field. */
    @Transactional
    public Customer create(Customer customer) {
        customer.setCustomerId(null);
        customer.setUser(null);
        if (customerRepository.existsByEmailIgnoreCase(customer.getEmail())) {
            throw ApiException.conflict("A customer with this email is already registered", "email");
        }
        if (customerRepository.existsByDriverLicenseNumber(customer.getDriverLicenseNumber())) {
            throw ApiException.conflict("This driver license is already registered", "driverLicenseNumber");
        }
        Customer saved = customerRepository.save(customer);
        audit.log("Customer registered", saved.getFullName() + " added to the directory (" + saved.getDriverLicenseNumber() + ")");
        return saved;
    }

    @Transactional
    public Customer update(UUID id, Customer changes) {
        Customer existing = getById(id);
        if (customerRepository.existsByEmailIgnoreCaseAndCustomerIdNot(changes.getEmail(), id)) {
            throw ApiException.conflict("A customer with this email is already registered", "email");
        }
        if (customerRepository.existsByDriverLicenseNumberAndCustomerIdNot(changes.getDriverLicenseNumber(), id)) {
            throw ApiException.conflict("This driver license is already registered", "driverLicenseNumber");
        }
        if (existing.getUser() != null && !existing.getEmail().equalsIgnoreCase(changes.getEmail())) {
            throw ApiException.conflict("This customer signs in with their email, so it can't be changed here", "email");
        }
        existing.setFullName(changes.getFullName());
        existing.setEmail(changes.getEmail());
        existing.setPhoneNumber(changes.getPhoneNumber());
        existing.setDriverLicenseNumber(changes.getDriverLicenseNumber());
        Customer saved = customerRepository.save(existing);
        audit.log("Customer updated", saved.getFullName() + " profile updated");
        return saved;
    }

    /**
     * A signed-in customer adds or updates their phone and driver license. If staff already
     * registered them as a walk-in customer with the same email and license, that profile is linked
     * instead of creating a duplicate (BR-01).
     */
    @Transactional
    public Customer saveOwnProfile(User user, ProfileRequest req) {
        String license = req.driverLicenseNumber().trim().toUpperCase();
        Customer customer = customerRepository.findByUser(user).orElse(null);
        if (customer == null) {
            customer = customerRepository.findByEmailIgnoreCase(user.getEmail()).orElse(null);
            if (customer != null && customer.getUser() != null) {
                throw ApiException.conflict("This email's customer profile belongs to another account", "driverLicenseNumber");
            }
            if (customer != null && !customer.getDriverLicenseNumber().equals(license)) {
                throw ApiException.conflict("This email is registered to a different driver license. Contact the VRMS team.", "driverLicenseNumber");
            }
            if (customer == null) {
                customer = new Customer();
                customer.setEmail(user.getEmail());
                customer.setFullName(user.getFullName());
            }
            customer.setUser(user);
        }
        UUID ownId = customer.getCustomerId();
        boolean licenseTaken = ownId == null
                ? customerRepository.existsByDriverLicenseNumber(license) && !license.equals(customer.getDriverLicenseNumber())
                : customerRepository.existsByDriverLicenseNumberAndCustomerIdNot(license, ownId);
        if (licenseTaken) {
            throw ApiException.conflict("This driver license is already registered", "driverLicenseNumber");
        }
        customer.setDriverLicenseNumber(license);
        customer.setPhoneNumber(req.phoneNumber() == null || req.phoneNumber().isBlank() ? null : req.phoneNumber());
        Customer saved = customerRepository.save(customer);
        audit.log("Customer profile saved", user.getFullName(), "Profile completed with license " + license);
        return saved;
    }

    /**
     * Deletes a customer (BR-06). Refused while they have an open booking or rental; otherwise their
     * closed contract history, uploaded documents and login account (if any) are removed with them.
     */
    @Transactional
    public void delete(UUID id) {
        Customer customer = getById(id);
        if (contractRepository.existsByCustomerAndContractStatusIn(customer, VehicleService.OPEN)) {
            throw ApiException.conflict("This customer has an open booking or rental. Complete or cancel it first.", null);
        }
        contractRepository.deleteAll(contractRepository.findByCustomer(customer));
        customerRepository.delete(customer);
        documentService.deleteAllFor(customer.getCustomerId());
        if (customer.getUser() != null) {
            userRepository.delete(customer.getUser());
        }
        audit.log("Customer removed", customer.getFullName() + " removed from the directory");
    }
}
