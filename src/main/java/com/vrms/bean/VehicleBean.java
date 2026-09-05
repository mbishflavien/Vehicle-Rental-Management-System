package com.vrms.bean;

import com.vrms.model.Vehicle;
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

@ManagedBean(name = "vehicleBean")
@ViewScoped
public class VehicleBean implements Serializable {

    private Vehicle vehicle = new Vehicle();
    private List<Vehicle> vehicleList = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadVehicles();
    }

    public void loadVehicles() {
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            vehicleList = session.createQuery("FROM Vehicle", Vehicle.class).getResultList();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public void saveVehicle() {
        if (vehicle.getPlateNumber() != null && !vehicle.getPlateNumber().matches("RAB[0-9]{3}[A-Z]")) {
            FacesContext.getCurrentInstance().addMessage("vehicleForm:plate",
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Invalid Plate Format", "Plate number must follow Rwandan format e.g., RAB123A"));
            return;
        }

        Session session = null;
        Transaction transaction = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();
            session.saveOrUpdate(vehicle);
            transaction.commit();

            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", "Vehicle registered successfully."));
            vehicle = new Vehicle();
            loadVehicles();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public void editVehicle(Vehicle v) {
        this.vehicle = v;
    }

    public void deleteVehicle(Vehicle v) {
        Session session = null;
        Transaction transaction = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            transaction = session.beginTransaction();

            // Merge detached entity into current session before deletion
            Vehicle managedVehicle = (Vehicle) session.merge(v);
            session.delete(managedVehicle);

            transaction.commit();
            loadVehicles();

            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Deleted", "Vehicle removed successfully."));
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
    public List<Vehicle> getVehicleList() { return vehicleList; }
}