package com.atguigu.gmall.search.vo;

import lombok.Data;

import java.util.List;

@Data
public class SearchResponseVO {

    // {attrId: null, attrName: 品牌， attrValues: ["{id: 4, name: 尚硅谷, logo: www.atguigu.com/xx.jpg}", "{id: 5, name: 华为, logo：}"]}
    private SearchResponseAttrVO brand; // 品牌借助于规格参数的数据模型来渲染
    // {attrId: null, attrName: 分类， attrValues: ["{id: 225, name: 手机}", "{id: 250, name: 笔记本}"]}
    private SearchResponseAttrVO category;

    // {attrId: 33, attrName: 电池， attrValues: ["3000", "4000"]}
    private List<SearchResponseAttrVO> attrs; // 过滤条件结果的封装

    private Long total; // 总记录数
    private Integer pageNum; // 页码
    private Integer pageSize; // 每页大小

    private List<GoodsVO> data;
}
