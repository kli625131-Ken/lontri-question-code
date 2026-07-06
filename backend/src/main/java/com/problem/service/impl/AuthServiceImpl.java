package com.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.problem.common.JwtUtil;
import com.problem.common.ResultCode;
import com.problem.common.TokenBlacklistService;
import com.problem.dto.LoginDTO;
import com.problem.entity.User;
import com.problem.mapper.UserMapper;
import com.problem.service.AuthService;
import com.problem.vo.LoginVO;
import com.problem.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserMapper userMapper;

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDTO.getUsername(), loginDTO.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            User user = getUserByUsername(loginDTO.getUsername());
            user.setLastLoginTime(LocalDateTime.now());
            userMapper.updateById(user);
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());

            return new LoginVO(token, jwtUtil.getExpiration() / 1000, toUserInfo(user));
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException(ResultCode.USERNAME_PASSWORD_ERROR.getMessage());
        }
    }

    @Override
    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            tokenBlacklistService.revokeToken(token);
        }
        SecurityContextHolder.clearContext();
    }

    @Override
    public UserInfoVO getCurrentUser(String username) {
        return toUserInfo(getUserByUsername(username));
    }

    private User getUserByUsername(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, username)
            .last("LIMIT 1"));

        if (user == null) {
            throw new IllegalArgumentException("User not found: " + username);
        }

        return user;
    }

    private UserInfoVO toUserInfo(User user) {
        return UserInfoVO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .realName(user.getRealName())
            .email(user.getEmail())
            .phone(user.getPhone())
            .avatarUrl(user.getAvatarUrl())
            .isAdmin(user.getIsAdmin())
            .globalSearchEnabled(user.getGlobalSearchEnabled())
            .roleId(user.getRoleId())
            .accountType(user.getAccountType())
            .expireAt(user.getExpireAt())
            .build();
    }
}
