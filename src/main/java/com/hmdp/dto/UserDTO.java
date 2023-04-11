package com.hmdp.dto;

import lombok.Data;

@Data
public class UserDTO {//存储程序必要地返回信息
    private Long id;
    private String nickName;
    private String icon;
}
