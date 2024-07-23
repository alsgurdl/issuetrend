package com.ict_final.issuetrend.service;


import com.ict_final.issuetrend.auth.TokenProvider;
import com.ict_final.issuetrend.dto.response.GoogleUserResponseDTO;
import com.ict_final.issuetrend.dto.response.LoginResponseDTO;
import com.ict_final.issuetrend.entity.LoginPath;
import com.ict_final.issuetrend.entity.User;
import com.ict_final.issuetrend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class GoogleLoginService {
    // AccessKey를 새롭게 발급받아 Map으로 포장해 주는 메서드.
    private Map<String, String> getTokenMap(User user) {
        String accessToken = tokenProvider.createAccessKey(user);

        Map<String, String> token = new HashMap<>();
        token.put("access_token", accessToken);
        return token;
    }

    private final UserRepository userRepository;
    private final TokenProvider tokenProvider;
    private final UserService userService;

    @Value("${google.client_id}")
    private String GOOGLE_CLIENT_ID;

    @Value("${google.client_pw}")
    private String GOOGLE_CLIENT_PW;

    @Value("${google.redirect_url}")
    private String GOOGLE_REDIRECT_URL;

    public LoginResponseDTO googleService(String code) {
        String accessToken = getGoogleAccessToken(code);
        log.info("accessToken: {}", accessToken);

        GoogleUserResponseDTO userDTO = getGoogleUserInfo(accessToken);
        log.info("userDTO: {}", userDTO);



        User foundUser
                = userRepository.findByEmail(userDTO.getEmail()).orElseThrow();

        log.info("foundUser: {}", foundUser);

        Map<String, String> token = getTokenMap(foundUser);

        // 기존에 로그인했던 사용자의 access token값을 update

        // 기존에 로그인했던 사용자의 refresh token값을 update
        foundUser.changeRefreshToken(token.get("refresh_token"));
        foundUser.changeRefreshExpiryDate(tokenProvider.getExpiryDate(token.get("refresh_token")));

        // 기존에 로그인했던 사용자의 access token값을 update
        foundUser.changeAccessToken(accessToken);
        foundUser.setLoginPath(LoginPath.GOOGLE);
        userRepository.save(foundUser);

        return new LoginResponseDTO(foundUser, token);
    }

    private GoogleUserResponseDTO getGoogleUserInfo(String accessToken) {
        String userInfoUri = "https://www.googleapis.com/oauth2/v3/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);

        RestTemplate template = new RestTemplate();
        ResponseEntity<GoogleUserResponseDTO> responseEntity
                = template.exchange(userInfoUri, HttpMethod.GET, new HttpEntity<>(headers), GoogleUserResponseDTO.class
        );

        return responseEntity.getBody();
    }

    private String getGoogleAccessToken(String code) {
        String requestURI = "https://oauth2.googleapis.com/token";


        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", GOOGLE_CLIENT_ID);
        params.add("client_secret", GOOGLE_CLIENT_PW);
        params.add("redirect_uri", GOOGLE_REDIRECT_URL);
        params.add("grant_type", "authorization_code");

        HttpEntity<Object> requestEntity = new HttpEntity<>(params, headers);

        RestTemplate template = new RestTemplate();

        ResponseEntity<Map> responseEntity
                = template.exchange(requestURI, HttpMethod.POST, requestEntity, Map.class);
        Map<String, Object> responseData = (Map<String, Object>) responseEntity.getBody();
        return (String) responseData.get("access_token");
    }
}