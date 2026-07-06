package com.problem.service;

import com.problem.dto.LoginDTO;
import com.problem.vo.LoginVO;
import com.problem.vo.UserInfoVO;

public interface AuthService {
    
    LoginVO login(LoginDTO loginDTO);
    
    void logout(String token);
    
    UserInfoVO getCurrentUser(String username);
}
