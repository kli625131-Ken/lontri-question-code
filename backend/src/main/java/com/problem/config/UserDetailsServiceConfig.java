package com.problem.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.problem.entity.User;
import com.problem.mapper.UserMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.time.LocalDateTime;
import java.util.Objects;

@Configuration
public class UserDetailsServiceConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserMapper userMapper) {
        return username -> {
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));

            if (user == null) {
                throw new UsernameNotFoundException("User not found: " + username);
            }

            boolean expiredTempUser = "TEMP".equalsIgnoreCase(user.getAccountType())
                && user.getExpireAt() != null
                && !user.getExpireAt().isAfter(LocalDateTime.now());
            if (expiredTempUser && !Objects.equals(user.getStatus(), 0)) {
                user.setStatus(0);
                userMapper.updateById(user);
            }

            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPassword())
            .authorities(Collections.emptyList())
            .disabled(!Objects.equals(user.getStatus(), 1) || expiredTempUser)
            .build();

            return userDetails;
        };
    }
}
