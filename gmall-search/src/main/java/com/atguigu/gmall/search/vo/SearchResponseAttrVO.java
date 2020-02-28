package com.atguigu.gmall.search.vo;

import lombok.Data;

import java.util.List;

@Data
public class SearchResponseAttrVO {

    private Long attrId;

    private String attrName;

    private List<String> attrValues;
}
