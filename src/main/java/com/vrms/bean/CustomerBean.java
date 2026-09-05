package com.vrms.bean;

import com.vrms.model.Customer;
import com.vrms.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@ManagedBean(name = "customerBean")
@ViewScoped
public class CustomerBean implements Serializable {

    private Customer customer = new Customer();
    private List<Customer> customerList = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadCustomers();
    }

    public void loadCustomers() {
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            customerList = session.createQuery("FROM Customer", Customer.class).getResultList();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public void saveCustomer() {
        if (customer.getDriverLicenseNumber() != null && !customer.getDriverLicenseNumber().startsWith("DL-")) {
            FacesContext.getCurrentInstance().addMessage("customerForm:license",
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Invalid Format", "Driver License must start with 'DL-'"));
            return;
        }

        Session session = null;
        Transaction transaction = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            session.saveOrUpdate(customer);
            transaction.commit();

            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", "Customer details saved successfully."));
            customer = new Customer();
            loadCustomers();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public void editCustomer(Customer c) {
        this.customer = c;
    }

    public void deleteCustomer(Customer c) {
        Session session = null;
        Transaction transaction = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();

            // Merge detached entity into current session before deletion
            Customer managedCustomer = (Customer) session.merge(c);
            session.delete(managedCustomer);

            transaction.commit();
            loadCustomers();

            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Deleted", "Customer removed successfully."));
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public List<Customer> getCustomerList() { return customerList; }
}