package com.datn.finrisk;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires a running MySQL database and Redis — run separately against real infrastructure")
class FinriskApplicationTests {

	@Test
	void contextLoads() {
	}

}
