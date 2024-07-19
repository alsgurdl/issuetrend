package com.ict_final.issuetrend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ict_final.issuetrend.entity.LoginPath;
import com.ict_final.issuetrend.entity.User;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.ArrayList;

@Setter
@Getter
@ToString
public class NaverUserDTO {
    private long userNo;
    @JsonProperty("response")
    private NaverAccount naverAccount;



    @Setter @Getter
    @ToString
    public static class NaverAccount {

        private String userNo;



        private String email;

        private String nickname;

        @JsonProperty("profile_image_url")
        private String profileImageUrl;





    }

    public User toEntity(String accessToken) {
        return User.builder()
                .userNo(this.userNo)
                .email(this.naverAccount.email)
                .nickname(this.naverAccount.nickname)
                .password("password!")
                .profileImage(this.naverAccount.profileImageUrl)
                .regionName("서울")
                .loginPath(LoginPath.NAVER)
                .favoriteKeywords(new ArrayList<>())
                .accessToken(accessToken)
                .build();
    }


}
