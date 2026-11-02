package com.mukhusin.myconsumerservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MyController {

    List<String> data;

    @GetMapping("/readdata")
    public List<String> readdata(){
        return data;
    }

}
