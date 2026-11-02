package com.mukhusin.configclient.config;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyConfig {
    private String compName;
    private int pincode;

    public MyConfig() {
        // TODO Auto-generated constructor stub
    }

    public String getCompName() {
        return compName;
    }

    public void setCompName(String compName) {
        this.compName = compName;
    }

    public int getPincode() {
        return pincode;
    }

    public void setPincode(int pincode) {
        this.pincode = pincode;
    }

}
