package com.mukhusin.configclient.controller;

import com.mukhusin.configclient.config.MyConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class ConfigController {

    @Autowired
    MyConfig config;

    @GetMapping("/values")
    public CompanyPojo getValues_actual() {
        return new CompanyPojo(config.getCompName(),config.getPincode());
    }
}
