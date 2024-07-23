package com.ict_final.issuetrend.dto.response;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.ict_final.issuetrend.entity.LoginPath;
import com.ict_final.issuetrend.entity.User;
import lombok.*;

import java.util.ArrayList;


@Getter @Setter @ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class GoogleUserResponseDTO {

    private String userNo;
    private String email;

    private String nickname;

    @JsonProperty("profile_image_url")
    private String profileImageUrl;


    public User toEntity(String accessToken) {
    return User.builder()
            .email(this.email)
            .nickname(this.nickname)
            .profileImage(this.profileImageUrl)
            .password("password!")
            .loginPath(LoginPath.GOOGLE)
            .regionName("서울")
            .favoriteKeywords(new ArrayList<>())
            .accessToken(accessToken)
            .build();
}

}
