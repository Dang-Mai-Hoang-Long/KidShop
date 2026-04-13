package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class DemoApplication {

	private static final TimeZone APP_TIME_ZONE = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");

	public static void main(String[] args) {
		TimeZone.setDefault(APP_TIME_ZONE);
		SpringApplication.run(DemoApplication.class, args);
	}

}
