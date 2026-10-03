package com.vrms.service;

import com.vrms.config.CacheConfig;
import com.vrms.exception.ApiException;
import com.vrms.model.Branch;
import com.vrms.repository.BranchRepository;
import com.vrms.repository.RentalContractRepository;
import com.vrms.repository.VehicleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BranchService {

    private final BranchRepository branchRepository;
    private final VehicleRepository vehicleRepository;
    private final RentalContractRepository contractRepository;
    private final AuditService audit;

    public BranchService(BranchRepository branchRepository, VehicleRepository vehicleRepository,
                         RentalContractRepository contractRepository, AuditService audit) {
        this.branchRepository = branchRepository;
        this.vehicleRepository = vehicleRepository;
        this.contractRepository = contractRepository;
        this.audit = audit;
    }

    @Cacheable(CacheConfig.BRANCHES)
    public List<Branch> getAll() {
        return List.copyOf(branchRepository.findAll(Sort.by("city", "name")));
    }

    public Branch getById(UUID id) {
        return branchRepository.findById(id).orElseThrow(() -> ApiException.notFound("Branch"));
    }

    /** Resolves an optional branch reference from a request; null stays null. */
    public Branch resolve(UUID id) {
        return id == null ? null : getById(id);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.BRANCHES, CacheConfig.FLEET}, allEntries = true)
    public Branch create(Branch branch) {
        branch.setBranchId(null);
        if (branchRepository.existsByNameIgnoreCase(branch.getName())) {
            throw ApiException.conflict("A branch with this name already exists", "name");
        }
        Branch saved = branchRepository.save(branch);
        audit.log("Branch added", saved.getName() + " (" + saved.getCity() + ") added");
        return saved;
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.BRANCHES, CacheConfig.FLEET}, allEntries = true)
    public Branch update(UUID id, Branch changes) {
        Branch existing = getById(id);
        if (branchRepository.existsByNameIgnoreCaseAndBranchIdNot(changes.getName(), id)) {
            throw ApiException.conflict("A branch with this name already exists", "name");
        }
        existing.setName(changes.getName());
        existing.setCity(changes.getCity());
        existing.setAddress(changes.getAddress());
        existing.setPhoneNumber(changes.getPhoneNumber());
        audit.log("Branch updated", existing.getName() + " details updated");
        return branchRepository.save(existing);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.BRANCHES, CacheConfig.FLEET}, allEntries = true)
    public void delete(UUID id) {
        Branch branch = getById(id);
        if (vehicleRepository.existsByBranch(branch) || contractRepository.existsByPickupBranch(branch)) {
            throw ApiException.conflict("Vehicles or contracts still use this branch. Move them first.", null);
        }
        branchRepository.delete(branch);
        audit.log("Branch removed", branch.getName() + " removed");
    }
}
