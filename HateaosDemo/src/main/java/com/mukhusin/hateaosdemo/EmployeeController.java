package com.mukhusin.hateaosdemo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmployeeController {

    @GetMapping("/employees/{id}")
    public Employee getEmpById(@PathVariable int id) {
        return new Employee(id, "emp name", "address mumbai", 123l, null);

    }

    @GetMapping("/employees")
    public List<Employee> getEmployees() {
        List<Employee> employees = new ArrayList<Employee>();
        employees.add(new Employee(1, "empName", "Mumbai", 1234, null));
        employees.add(new Employee(2, "empName", "Pune", 100, null));
        employees.add(new Employee(123, "emsjcb", "Mumbai", 1, null));
        return employees;
    }

    @GetMapping("/employees/{empid}/projects/{id}")
    public ResponseEntity<Project> getProjectById(@PathVariable int empid, @PathVariable int id) {
        return getProjectsByEmployeeId(empid).stream()
                .filter(project -> project.getProjectId() == id)
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());

    }

    @GetMapping("/employees/{empid}/projects")
    public List<Project> getProjectsByEmployeeId(@PathVariable int empid) {
        List<Project> projects = new ArrayList<>();
        if (empid == 1) {
            projects.add(new Project(1, "Banking app", "JAVA"));
            projects.add(new Project(2, "Employee app", "JAVA"));
        } else if (empid == 2) {
            projects.add(new Project(1, "Banking app", "JAVA"));
        } else if (empid == 123) {
            projects.add(new Project(1, "Banking app", "JAVA"));
            projects.add(new Project(2, "Employee app", "JAVA"));
            projects.add(new Project(7, "Library app", "SPRING"));
        }
        return projects;

    }

    @GetMapping("/employees-hateaos/{id}")
    public ResponseEntity<Employee> getEmployee_hateaos(@PathVariable int id) {

        Employee employee = new Employee(id, "emp name", "address mumbai", 123l, null);

        employee.add(
                WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(EmployeeController.class).getEmployee_hateaos(id))
                        .withSelfRel(),
                WebMvcLinkBuilder.linkTo(WebMvcLinkBuilder.methodOn(EmployeeController.class).getEmployees())
                        .withRel("allEmployees"),
                WebMvcLinkBuilder
                        .linkTo(WebMvcLinkBuilder.methodOn(EmployeeController.class).getProjectsByEmployeeId(id))
                        .withRel("projects"));

        ResponseEntity<Employee> entity = new ResponseEntity<Employee>(employee, HttpStatus.OK);
        return entity;
    }

    @GetMapping("/employees-hateaos-methods/{empid}")
    public Employee getEmployees_hateaos_methods(@PathVariable int empid) {
        Link projectsLink = WebMvcLinkBuilder
                .linkTo(WebMvcLinkBuilder.methodOn(EmployeeController.class).getProjectsByEmployeeId(empid))
                .withRel("allProjects");

        Employee employee = new Employee(empid, "empName", "empAddress", 1234, null);
        employee.add(projectsLink);
        return employee;

    }


}

