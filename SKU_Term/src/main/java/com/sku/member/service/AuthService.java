package com.sku.member.service;

import java.util.Map;

public interface AuthService {
    Map<String, String> login(String email, String password);

    Map<String, String> reissue(String refreshToken);

    void logout(String studentNumber);


}