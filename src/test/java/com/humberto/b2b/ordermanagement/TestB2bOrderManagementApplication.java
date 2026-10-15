package com.humberto.b2b.ordermanagement;

import org.springframework.boot.SpringApplication;

public class TestB2bOrderManagementApplication {

    public static void main(String[] args) {
        SpringApplication.from(B2bOrderManagementApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
