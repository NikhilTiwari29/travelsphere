package com.nikhil.common_lib.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nikhil.common_lib.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private String jwt;
    private String message;
    private String title;
    private UserDTO user;
}
