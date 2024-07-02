package com.ict_final.issuetrend.api;

import com.ict_final.issuetrend.auth.TokenUserInfo;
import com.ict_final.issuetrend.dto.request.LoginRequestDTO;
import com.ict_final.issuetrend.dto.request.UserSignUpRequestDTO;
import com.ict_final.issuetrend.dto.request.UserUpdateInfoRequestDTO;
import com.ict_final.issuetrend.dto.response.LoginResponseDTO;
import com.ict_final.issuetrend.dto.response.NickResponseDTO;
import com.ict_final.issuetrend.dto.response.UserSignUpResponseDTO;
import com.ict_final.issuetrend.entity.User;
import com.ict_final.issuetrend.repository.UserRepository;
import com.ict_final.issuetrend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.FileCopyUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/issue-trend")
public class UserController {
    private final UserService userService;
    private final UserRepository userRepository;

    // 이메일 중복 확인 요청 처리
    @GetMapping("/check")
    public ResponseEntity<?> check(String email) {
        log.info("Received email check request for: {}", email);
        if (email.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("이메일이 존재하지 않습니다.");
        }
        boolean resultFlag = userService.isDuplicate(email);
        log.info("Email duplication check result: {}", resultFlag);
        return ResponseEntity.ok().body(resultFlag);
    }

    @GetMapping("nick-check")
    public ResponseEntity<?> nickCheck(String nickname) {
        if (nickname.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("닉네임이 존재하지 않습니다.");
        }
        boolean nickDuplicate = userService.nickDuplicate(nickname);
        log.info("nick nickDuplicate check result: {}", nickDuplicate);
        return ResponseEntity.ok().body(nickDuplicate);
    }


    // mypage 정보 변경하기 전 현재 비밀번호 한 번 더 검증
    @PostMapping("/password-check")
    public ResponseEntity<?> pwCheck(@AuthenticationPrincipal TokenUserInfo tokenUserInfo,
                                     @RequestBody Map<String, String> data) {
        String userEmail = tokenUserInfo.getEmail();
        String checkPw = data.get("password");

        if(!userService.isDuplicate(userEmail)) {
            return ResponseEntity.badRequest().body("사용자가 존재하지 않습니다.");
        }
        if(userService.isMatch(userEmail, checkPw)) {
          return ResponseEntity.ok().body("비밀번호가 일치합니다.");
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("비밀번호가 일치하지 않습니다.");
        }
    }

    // 회원가입 요청 처리
    @PostMapping
    public ResponseEntity<?> signUp(
            @Validated @RequestPart("user") UserSignUpRequestDTO dto,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage,
            BindingResult result
    ) {
        log.info("/issue-trend POST! - {}", dto);
        ResponseEntity<FieldError> resultEntity = getFieldErrorResponseEntity(result);
        if (resultEntity != null) return resultEntity;
        try {
            String uploadedFilePath = null;
            if (profileImage != null) {
                log.info("attached file name: {}", profileImage.getOriginalFilename());
                // 전달받은 프로필 이미지를 먼저 지정된 경로에 저장한 후 저장 경로를 DB에 세팅하자.
                uploadedFilePath = userService.uploadProfileImage(profileImage);
            }
            UserSignUpResponseDTO responseDTO = userService.create(dto, uploadedFilePath);
            log.info("responseDTO: {}", responseDTO);
            return ResponseEntity.ok().body(responseDTO);
        } catch (IOException e) {
            log.error("An unexpected error occurred!", e);
            e.printStackTrace();
            throw new RuntimeException("An unexpected error occurred!", e);
        }
    }

    // 로그인 요청 처리
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Validated @RequestBody LoginRequestDTO dto,
            BindingResult result
    ) {
        log.info("/issue-trend/login POST! - {}", dto);
        ResponseEntity<FieldError> resultEntity2 = getFieldErrorResponseEntity(result);
        if (resultEntity2 != null) return resultEntity2;
        LoginResponseDTO responseDTO = userService.login(dto);
        return ResponseEntity.ok().body(responseDTO);
    }


    @GetMapping("/kakaologin")
    public ResponseEntity<?> kakaoLogin(String code) {
        log.info("/api/auth/kakaoLogin - GET! code: {}", code);
        LoginResponseDTO responseDTO = userService.kakaoService(code);

        return ResponseEntity.ok().body(responseDTO);
    }

    @GetMapping("/logout")
    public ResponseEntity<?> logout(
            @AuthenticationPrincipal TokenUserInfo userInfo
    ) {
        log.info("/api/auth/logout - GET! - user: {}", userInfo.getEmail());
        String result = userService.logout(userInfo);
        return ResponseEntity.ok().body(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> tokenRequest) {
        log.info("/api/auth/refresh: POST! - tokenRequest: {}", tokenRequest);
        String renewalAccessToken = userService.renewalAccessToken(tokenRequest);
        if (renewalAccessToken != null) {
            return ResponseEntity.ok().body(Map.of("accessToken", renewalAccessToken));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refresh token");
    }

    // 프로필 사진 이미지 데이터를 클라이언트에게 응답 처리
    @GetMapping("/load-profile")
    public ResponseEntity<?> loadFile(
            @AuthenticationPrincipal TokenUserInfo userInfo
    ) {
        log.info("-------userinfo {}", userInfo.getUserNo());


            // 1. 프로필 사진의 경로부터 얻어야 한다.
           String filePath = userService.findProfilePath(userInfo.getUserNo());
            log.info("filePath: {}", filePath);

        if (filePath != null){
            return ResponseEntity.ok().body(filePath);
        }else{
            return ResponseEntity.notFound().build();
        }

    }

    public static MediaType findExtensionAndGetMediaType(String filePath) {

        // 파일 경로에서 확장자 추출
        // C:/todo_upload/nlskdnakscnlknklcs_abc.jpg
        String ext
                = filePath.substring(filePath.lastIndexOf(".") + 1);

        // 추출한 확장자를 바탕으로 MediaType을 설정 -> Header에 들어갈 Content-type이 됨.
        switch (ext.toUpperCase()) {
            case "JPG":
            case "JPEG":
                return MediaType.IMAGE_JPEG;
            case "PNG":
                return MediaType.IMAGE_PNG;
            case "GIF":
                return MediaType.IMAGE_GIF;
            default:
                return null;
        }
    }

    // BindingResult 에서 유효성 검사 오류가 있는지 확인
    private static ResponseEntity<FieldError> getFieldErrorResponseEntity(BindingResult result) {
        if (result.hasErrors()) {
            log.warn(result.toString());
            return ResponseEntity.badRequest()
                    .body(result.getFieldError());
        }
        return null;
    }


    @Operation(summary = "새로운 비밀번호 전송", description = "새로운 비밀번호를 생성하여 요청받은 이메일로 보내는 메서드 입니다.")
    @Parameter(name = "email", description = "가입할 때 작성한 이메일을 입력하세요.", example = "aaa1234@gmail.com", required = true)
    @GetMapping("/send-password")
    public ResponseEntity<?> sendEmail(@RequestParam String email) {

        if (userService.isDuplicate(email)) {
            try {
                userService.sendNewPassword(email);
                return new ResponseEntity<>("Email sent successfully!", HttpStatus.OK);
            } catch (Exception e) {
                e.printStackTrace();
                return new ResponseEntity<>("Error sending email: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            return new ResponseEntity<>("가입 정보가 없습니다", HttpStatus.BAD_REQUEST);
        }
    }

    // 닉네임으로 유저 정보 찾기
    @GetMapping("/find-user")
    public ResponseEntity<?> findUser(@RequestParam("nickname") String nickname) {
        User user = userRepository.findByUserNickname(nickname).orElseThrow();
        NickResponseDTO nickResponseDTO = new NickResponseDTO(user);
        return ResponseEntity.ok().body(nickResponseDTO);
    }

//    뉴스레터 확인용 (실제 사용 X)
//    @GetMapping("/send-newsletter")
//    public ResponseEntity<?> sendNewsLetter() {
//        userService.sendNewsLetter();
//        return null;
//    }

    // 회원정보변경 요청 처리
    @PostMapping("/update-my-info")
    public ResponseEntity<?> updateUserInfo(@AuthenticationPrincipal TokenUserInfo tokenUserInfo,
                                            @Validated @RequestPart("user") UserUpdateInfoRequestDTO dto,
                                            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage,
                                            BindingResult result) throws IOException {



        // log.info("/issue-trend/update-my-info POST! - {}", dto);
        log.info("회원정보 변경 메서드 들어옴");
        log.info("tokenInfo: {} dto: {}", tokenUserInfo, dto);
        log.info("profile_filename: {}", profileImage.getOriginalFilename());
        ResponseEntity<FieldError> resultEntity = getFieldErrorResponseEntity(result);
        if (resultEntity != null) return resultEntity;

        String email = tokenUserInfo.getEmail();

        String newNick = dto.getNickname();
        String newPw = dto.getPassword();
        String newRegionName = dto.getRegionName();
        log.info("dto.getFavoriteKeywords: {}", dto.getFavoriteKeywords());
        List<String> newFavoriteKeywords = dto.getFavoriteKeywords();
        log.info("newFavoriteKeywords: {}", newFavoriteKeywords); // [dddd, ddd, sdaDS]

        String filePath = null;
        if(profileImage != null ) {
            filePath = userService.uploadProfileImage(profileImage);
        }
        userService.updateMyInfo(email, newNick, newPw, newRegionName, newFavoriteKeywords, filePath);


        //profileImage

        log.info("dto.getFavoriteKeywords(): {}", dto.getFavoriteKeywords());
        return ResponseEntity.ok().body("success");

    }
    // 주석 지우지 말아주세요
    // tokenInfo: TokenUserInfo(userNo=44, email=ilypsj@naver.comddd) dto: UserUpdateInfoRequestDTO(password=rkskdy1111, regionName=서울, nickname=가나요d, favoriteKeywords=[s, sss, sssss])
    // profileImage: null
    //////////////////////////////////////////////////////////////////

        /*
        try {
            String uploadedFilePath = null;
            if (profileImage != null) {
                log.info("attached file name: {}", profileImage.getOriginalFilename());
                // 전달받은 프로필 이미지를 먼저 지정된 경로에 저장한 후 저장 경로를 DB에 세팅하자.
                uploadedFilePath = userService.uploadProfileImage(profileImage);
            }
            UserSignUpResponseDTO responseDTO = userService.create(dto, uploadedFilePath);
            log.info("responseDTO: {}", responseDTO);
            return ResponseEntity.ok().body(responseDTO);
        } catch (IOException e) {
            log.error("An unexpected error occurred!", e);
            e.printStackTrace();
            throw new RuntimeException("An unexpected error occurred!", e);
        }
        */

    @DeleteMapping("/delete")// /{userNo}
    public ResponseEntity<?> deleteUser(@AuthenticationPrincipal TokenUserInfo tokenUserInfo) {
        // @PathVariable String userNo
        log.info("/delete 들어옴");
        log.info("TokenUserInfo: {}", tokenUserInfo);
        // String userNo = tokenUserInfo.getUserNo();

        return ResponseEntity.ok().body("SUCCESS");
    }


}
