package com.atguigu.gmall.search.vo;

import lombok.Data;

/**
 * search?keyword=小米&brandId=11,12&categoryId=225,250&props=33:3000-4000-5000,34:5-6-7&order=1:desc&priceFrom=1000&priceTo=2000&pageNum=2&store=true
 */
@Data
public class SearchParamVO {

    private String keyword; // 用户输入的关键字

    private Long[] brandId;  // 品牌的id

    private Long[] categoryId; // 分类的id

    private String[] props; // 规格参数的过滤条件。 props=33:3000-4000-5000,34:5-6-7

    private String order; // 排序 冒号前是排序字段（0：得分 1：价格  2：销量 3：新品） 冒号后是升降序

    private Boolean store; // 是否有货

    // 价格区间
    private Double priceFrom;
    private Double priceTo;

    // 分页参数
    private Integer pageNum = 1;
    private final Integer pageSize = 20; // 每页数据的大小，不允许用户取自定义
}
