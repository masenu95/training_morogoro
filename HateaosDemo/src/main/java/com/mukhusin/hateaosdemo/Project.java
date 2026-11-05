package com.mukhusin.hateaosdemo;

public class Project {
    private int projectId;
    private String projectName;
    private String technology;

    public Project() {
        // TODO Auto-generated constructor stub
    }

    public Project(int projectId, String projectName, String technology) {
        super();
        this.projectId = projectId;
        this.projectName = projectName;
        this.technology = technology;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getTechnology() {
        return technology;
    }

    public void setTechnology(String technology) {
        this.technology = technology;
    }

    @Override
    public String toString() {
        return "Project [projectId=" + projectId + ", projectName=" + projectName + ", technology=" + technology + "]";
    }
}
