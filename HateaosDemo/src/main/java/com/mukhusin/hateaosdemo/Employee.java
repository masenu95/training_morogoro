package com.mukhusin.hateaosdemo;

import org.springframework.hateoas.RepresentationModel;

import java.util.List;

public class Employee extends RepresentationModel<Employee> {

    int empId;
    String empName;
    String empAddress;
    long salary;
    List<Project> projects;

    public List<Project> getProjects() {
        return projects;
    }

    public void setProjects(List<Project> projects) {
        this.projects = projects;
    }

    public Employee(int empId, String empName, String empAddress, long salary, List<Project> projects) {
        super();
        this.empId = empId;
        this.empName = empName;
        this.empAddress = empAddress;
        this.salary = salary;
        this.projects=projects;
    }

    public Employee() {
        // TODO Auto-generated constructor stub
    }

    public int getEmpId() {
        return empId;
    }

    public void setEmpId(int empId) {
        this.empId = empId;
    }

    public String getEmpName() {
        return empName;
    }

    public void setEmpName(String empName) {
        this.empName = empName;
    }

    public String getEmpAddress() {
        return empAddress;
    }

    public void setEmpAddress(String empAddress) {
        this.empAddress = empAddress;
    }

    public long getSalary() {
        return salary;
    }

    public void setSalary(long salary) {
        this.salary = salary;
    }

    @Override
    public String toString() {
        return "Employee [empId=" + empId + ", empName=" + empName + ", empAddress=" + empAddress + ", salary=" + salary
                + "]"+projects;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((empAddress == null) ? 0 : empAddress.hashCode());
        result = prime * result + empId;
        result = prime * result + ((empName == null) ? 0 : empName.hashCode());
        result = prime * result + (int) (salary ^ (salary >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Employee other = (Employee) obj;
        if (empAddress == null) {
            if (other.empAddress != null)
                return false;
        } else if (!empAddress.equals(other.empAddress))
            return false;
        if (empId != other.empId)
            return false;
        if (empName == null) {
            if (other.empName != null)
                return false;
        } else if (!empName.equals(other.empName))
            return false;
        if (salary != other.salary)
            return false;
        return true;
    }

}
