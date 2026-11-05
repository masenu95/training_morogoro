package com.mukhusin.configclient.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

@RestController
public class DemoController {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @GetMapping("config-data")
    public String getData(){

            try {
                return "name:-"+jdbcTemplate.getDataSource().getConnection().getCatalog();
            } catch (SQLException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return null;
    }
}
