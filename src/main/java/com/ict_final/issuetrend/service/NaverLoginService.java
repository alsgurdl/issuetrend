package com.ict_final.issuetrend.service;

import com.ict_final.issuetrend.auth.TokenProvider;
import com.ict_final.issuetrend.dto.response.LoginResponseDTO;
import com.ict_final.issuetrend.dto.response.NaverUserDTO;
import com.ict_final.issuetrend.entity.LoginPath;
import com.ict_final.issuetrend.entity.User;
import com.ict_final.issuetrend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.juli.logging.Log;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
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
public class NaverLoginService {
    private final UserRepository userRepository;
    private final TokenProvider tokenProvider;
    // 이메일 중복확인
    public boolean isDuplicate(String email) {
        if (userRepository.existsByEmail(email)) {
            log.info("중복된 이메일입니다. -> {}", email);
            return true;
        } else return false;
    }

    // AccessKey를 새롭게 발급받아 Map으로 포장해 주는 메서드.
    private Map<String, String> getTokenMap(User user) {
        String accessToken = tokenProvider.createAccessKey(user);

        Map<String, String> token = new HashMap<>();
        token.put("access_token", accessToken);
        return token;
    }

    // 네이버 토큰 받을때 필요한 것
    @Value("${naver.client_id}")
    private String NAVER_CLIENT_ID;

    @Value("${naver.redirect_url}")
    private String NAVER_REDIRECT_URL;

    @Value("${naver.client_secret}")
    private String NAVER_CLIENT_SECRET;


    // 사용자 정보 얻어오는 호출 등 사용
    public LoginResponseDTO naverLoginService(String code, String state) {

        String accessToken = getNaverAccessToken(code, state);
        log.info("access_token: {}", accessToken);

        // 토큰을 통해 사용자 정보 가져오기
        NaverUserDTO userDTO = getNaverUserInfo(accessToken);

        // 네이버 로그인 이메일 중복 체크
        // 이메일이 중복되지 않았으면 이전에 로그인한적 없는 신규 회원 -> DB에 저장.
        if(!isDuplicate(userDTO.getNaverAccount().getEmail())) {
            // User saved = userRepository.save(userDTO.toEntity(accessToken, passwordEncoder));
            User newNaverUser = userDTO.toEntity(null);
            Map<String, String> tokenMap = getTokenMap(newNaverUser);
            newNaverUser.setAccessToken(tokenMap.get("access_token"));
            User saved = userRepository.save(newNaverUser);
            return new LoginResponseDTO(newNaverUser, tokenMap);
        }
        // 이메일이 중복되었으면 로그인한 이메일 -> DB에 넣지 않는다.
        User foundUser
                = userRepository.findByEmail(userDTO.getNaverAccount().getEmail()).orElseThrow();

        // 우리 사이트에서 사용하는 jwt를 생성.
        Map<String, String> token = getTokenMap(foundUser);

        // 기존에 로그인했던 사용자의 access token값을 update

        // 기존에 로그인했던 사용자의 refresh token값을 update
        foundUser.changeRefreshToken(token.get("refresh_token"));
        foundUser.changeRefreshExpiryDate(tokenProvider.getExpiryDate(token.get("refresh_token")));

        // 기존에 로그인했던 사용자의 access token값을 update
        foundUser.changeAccessToken(accessToken);
        foundUser.setLoginPath(LoginPath.NAVER);
        userRepository.save(foundUser);

        return new LoginResponseDTO(foundUser, token);
    }

    // 사용자 정보 가져오기
    private NaverUserDTO getNaverUserInfo(String accessToken) {
        // 요청 uri
        String redirectURI = "https://openapi.naver.com/v1/nid/me";

        // 요청헤더
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);

        // 요청 보내기
        RestTemplate template = new RestTemplate();
        ResponseEntity<NaverUserDTO> responseEntity
                = template.exchange(redirectURI, HttpMethod.GET, new HttpEntity<>(headers), NaverUserDTO.class );


        // 응답 바디 꺼내기
        NaverUserDTO responseData = responseEntity.getBody();
        log.info("user profile:{}", responseData);

        return responseData;
    }

    // 토큰만 뽑는 부분
    private String getNaverAccessToken(String code, String state) {
        // 요청 uri
        String requestURI = "https://nid.naver.com/oauth2.0/token";

        // 요청 헤더
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // 요청 바디(파라미터) 설정
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code"); // 네이버 공식 문서 기준 값으로 세팅
        params.add("client_id", NAVER_CLIENT_ID); // 네이버 디벨로퍼 REST API 키
        params.add("client_secret", NAVER_CLIENT_SECRET); // 네이버 디벨로퍼 client secret
        params.add("redirect_uri", NAVER_REDIRECT_URL); // 네이버 디벨로퍼 등록된 redirect uri
        params.add("code", code); // 프론트에서 인가 코드 요청시 전달받은 코드값
        params.add("state", state); // 프론트에서 인가 코드 요청시 전달받은 state값

        // 헤더와 바디 정보를 합치기 위해 HttpEntity 객체 생성
        HttpEntity<Object> requestEntity = new HttpEntity<>(params, headers);

        // 카카오 서버로 POST 통신
        RestTemplate template = new RestTemplate();
        ResponseEntity<Map> responseEntity
                = template.exchange(requestURI, HttpMethod.POST, requestEntity, Map.class);


        // 응답 데이터에서 필요한 정보를 가져오기
        Map<String, Object> responseData = (Map<String, Object>) responseEntity.getBody();
        log.info("토큰 요청 응답 데이터:{}", responseData);


        // access_token의 토큰값을 스트링 타입으로 받고 리턴하기
        return (String) responseData.get("access_token");

    }


}
