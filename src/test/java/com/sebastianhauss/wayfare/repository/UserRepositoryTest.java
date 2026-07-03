package com.sebastianhauss.wayfare.repository;

import com.sebastianhauss.wayfare.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    @Test
    void findByEmail_returnsSavedUser() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHash("hashed");
        repository.saveAndFlush(user);

        assertThat(repository.findByEmail("user@example.com")).isPresent();
    }

    @Test
    void findByEmail_returnsEmpty_whenEmailDoesNotExist() {
        assertThat(repository.findByEmail("missing@example.com")).isEmpty();
    }
}
