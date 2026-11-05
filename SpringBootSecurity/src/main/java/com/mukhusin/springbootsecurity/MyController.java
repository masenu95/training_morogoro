package com.mukhusin.springbootsecurity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MyController {

    @GetMapping("/hello")
    public String function1() {
        return "hello world";
    }

    @GetMapping("/bye")
    public String function2() {
        return "hello world";
    }


    @PostMapping("/secure")
    public String function3() {
        return "Secure endpoint";
    }

}
