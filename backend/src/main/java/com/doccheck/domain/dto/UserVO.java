package com.doccheck.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserVO {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String role;
}
